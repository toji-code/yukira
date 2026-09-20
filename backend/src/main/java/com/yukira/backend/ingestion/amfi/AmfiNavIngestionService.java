package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AmfiNavIngestionService {

    private final AmfiNavParser parser;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public AmfiNavIngestionService(
        AmfiNavParser parser,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.parser = parser;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    /**
     * Ingests parsed AMFI records from a SourceArtifact into the bitemporal nav_observation ledger.
     * Enforces the approved six-dimensional quality taxonomy, deterministic validation,
     * immutable revision management, and explicit analytical EOD availability convention.
     */
    @Transactional
    public IngestionSummary ingestArtifact(SourceArtifact artifact) {
        if (artifact == null || artifact.getPayloadBlob() == null) {
            throw new IllegalArgumentException("SourceArtifact or payload_blob must not be null");
        }

        List<AmfiNavRecord> records = parser.parse(artifact.getPayloadBlob());
        List<String> messages = new ArrayList<>();

        int totalParsed = records.size();
        int validRows = 0;
        int ingested = 0;
        int revisions = 0;
        int duplicatesSkipped = 0;
        int issuesCreated = 0;

        // Pre-load all registered SchemeOptions into local memory to prevent N+1 database roundtrips on large feeds
        Map<String, SchemeOption> amfiCodeMap = new HashMap<>();
        Map<String, SchemeOption> isinMap = new HashMap<>();
        for (SchemeOption opt : schemeOptionRepository.findAll()) {
            if (opt.getAmfiCode() != null && !opt.getAmfiCode().isBlank()) {
                amfiCodeMap.put(opt.getAmfiCode().trim(), opt);
            }
            if (opt.getIsin() != null && !opt.getIsin().isBlank()) {
                isinMap.put(opt.getIsin().trim(), opt);
            }
        }

        // Group valid records by schemeOptionId to evaluate sequential jumps and ordering
        Map<Long, List<AmfiNavRecord>> schemeGroupedRecords = new HashMap<>();

        for (AmfiNavRecord rec : records) {
            if (!rec.isValid()) {
                // Record validation issue for malformed raw row (capped at 50 to avoid connection pool exhaustion)
                if (issuesCreated < 50) {
                    ValidationIssue issue = new ValidationIssue(
                        "SOURCE_ARTIFACT",
                        artifact.getId(),
                        "MALFORMED_ROW",
                        String.format("Line %d: %s [raw: %s]", rec.lineNumber(), rec.parseErrorMessage(), rec.rawLine())
                    );
                    issue.setQualityAssessment("INVALID");
                    issue.setVerificationStatus("UNVERIFIED");
                    validationIssueRepository.save(issue);
                }
                issuesCreated++;
                continue;
            }

            validRows++;

            // Resolve SchemeOption identity via in-memory lookup
            // Prioritize ISIN exact match first, since AMFI can reuse scheme codes across Growth and IDCW options
            String schemeCode = rec.schemeCode() != null ? rec.schemeCode().trim() : "";
            SchemeOption schemeOption = null;
            if (rec.isinGrowth() != null && !rec.isinGrowth().isBlank()) {
                schemeOption = isinMap.get(rec.isinGrowth().trim());
            }
            if (schemeOption == null && !schemeCode.isEmpty()) {
                SchemeOption candidate = amfiCodeMap.get(schemeCode);
                if (candidate != null) {
                    // Only match by AMFI code if record does not have a conflicting non-empty ISIN
                    if (candidate.getIsin() == null || candidate.getIsin().isBlank()
                        || rec.isinGrowth() == null || rec.isinGrowth().isBlank()
                        || candidate.getIsin().equalsIgnoreCase(rec.isinGrowth().trim())) {
                        schemeOption = candidate;
                    }
                }
            }

            if (schemeOption == null) {
                // Unknown scheme identity -> Emit explicit diagnostic, preserve raw artifact, do not invent fake identity
                // Cap database-persisted issues for unmapped batch items to 50 to avoid DB connection exhaustion on massive feeds
                if (issuesCreated < 50) {
                    ValidationIssue issue = new ValidationIssue(
                        "SOURCE_ARTIFACT",
                        artifact.getId(),
                        "UNMAPPED_SOURCE_SCHEME",
                        String.format("Line %d: No SchemeOption mapped for AMFI code '%s' or ISIN '%s'",
                            rec.lineNumber(), rec.schemeCode(), rec.isinGrowth())
                    );
                    issue.setQualityAssessment("SUSPICIOUS");
                    issue.setVerificationStatus("UNVERIFIED");
                    validationIssueRepository.save(issue);
                }
                issuesCreated++;
                continue;
            }

            schemeGroupedRecords.computeIfAbsent(schemeOption.getId(), k -> new ArrayList<>()).add(rec);
        }

        // Process grouped observations per scheme option sorted chronologically
        for (Map.Entry<Long, List<AmfiNavRecord>> entry : schemeGroupedRecords.entrySet()) {
            Long schemeOptionId = entry.getKey();
            List<AmfiNavRecord> schemeRecords = entry.getValue();

            // Sort chronologically by effective date
            schemeRecords.sort(Comparator.comparing(AmfiNavRecord::navDate));

            SchemeOption option = schemeOptionRepository.findById(schemeOptionId).orElseThrow();
            BigDecimal prevNav = null;

            for (AmfiNavRecord rec : schemeRecords) {

                // 1. Boundary check: NAV must be strictly positive (> 0.0001)
                if (rec.navValue().compareTo(new BigDecimal("0.00010000")) <= 0) {
                    ValidationIssue issue = new ValidationIssue(
                        "SCHEME_OPTION",
                        schemeOptionId,
                        "NON_POSITIVE_NAV_BOUNDARY",
                        String.format("Line %d: Non-positive NAV %s on %s for scheme option %d",
                            rec.lineNumber(), rec.navValue(), rec.navDate(), schemeOptionId)
                    );
                    issue.setQualityAssessment("INVALID");
                    issue.setVerificationStatus("UNVERIFIED");
                    validationIssueRepository.save(issue);
                    issuesCreated++;
                    continue; // Skip invalid observation
                }

                // 2. Candidate Jump Check: Single-day simple return |(NAV_t / NAV_{t-1}) - 1| > 0.20
                boolean isSuspiciousJump = false;
                if (prevNav != null && prevNav.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal jump = rec.navValue().subtract(prevNav)
                        .divide(prevNav, 8, RoundingMode.HALF_UP).abs();
                    if (jump.compareTo(new BigDecimal("0.20")) > 0) {
                        isSuspiciousJump = true;
                        ValidationIssue issue = new ValidationIssue(
                            "SCHEME_OPTION",
                            schemeOptionId,
                            "CANDIDATE_SUSPICIOUS_NAV_JUMP",
                            String.format("Candidate jump check breached: %s on %s vs prev %s (change %s%%). Unapproved candidate threshold.",
                                rec.navValue(), rec.navDate(), prevNav, jump.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP))
                        );
                        issue.setQualityAssessment("SUSPICIOUS");
                        issue.setVerificationStatus("UNVERIFIED");
                        validationIssueRepository.save(issue);
                        issuesCreated++;
                    }
                }
                prevNav = rec.navValue();

                // 3. Temporal availability convention
                // AMFI historical text exports omit publication time -> explicit analytical modeling convention
                OffsetDateTime analyticalAvailability = OffsetDateTime.of(
                    rec.navDate().atTime(23, 59, 59, 999000000),
                    ZoneOffset.ofHoursMinutes(5, 30)
                );

                // 4. Bitemporal Revision & Immutability Management
                List<NavObservation> existingObs = navObservationRepository
                    .findBySchemeOptionIdAndEffectiveDate(schemeOptionId, rec.navDate());

                if (existingObs.isEmpty()) {
                    // Case A: Initial observation
                    NavObservation obs = new NavObservation(
                        option, rec.navDate(), rec.navValue(), 1, analyticalAvailability
                    );
                    obs.setSourceArtifact(artifact);
                    obs.setRevisionStatus("ORIGINAL");
                    obs.setTemporalStatus("CURRENT"); // Dimension 4: Freshness (CURRENT / STALE)
                    obs.setQualityAssessment(isSuspiciousJump ? "SUSPICIOUS" : "VALID");
                    obs.setVerificationStatus("VERIFIED");
                    obs.setPresenceStatus("AVAILABLE");
                    navObservationRepository.save(obs);
                    ingested++;
                } else {
                    // Find latest revision
                    NavObservation latestExisting = existingObs.stream()
                        .max(Comparator.comparingInt(NavObservation::getRevisionSeq))
                        .orElseThrow();

                    if (latestExisting.getNavValue().compareTo(rec.navValue()) == 0) {
                        // Case B: Duplicate identical observation -> Skip redundant row, log Integrity DUPLICATE
                        duplicatesSkipped++;
                    } else {
                        // Case C: Altered NAV (Restatement / Conflict)
                        // Preserve immutability: do NOT overwrite. Update is_latest_revision flag on previous
                        latestExisting.setLatestRevision(false);
                        latestExisting.setRevisionStatus("SUPERSEDED");
                        navObservationRepository.save(latestExisting);

                        int nextSeq = latestExisting.getRevisionSeq() + 1;
                        NavObservation restated = new NavObservation(
                            option, rec.navDate(), rec.navValue(), nextSeq, analyticalAvailability
                        );
                        restated.setSourceArtifact(artifact);
                        restated.setRevisionStatus("REVISED");
                        restated.setTemporalStatus("CURRENT");
                        restated.setQualityAssessment(isSuspiciousJump ? "SUSPICIOUS" : "VALID");
                        restated.setVerificationStatus("VERIFIED");
                        restated.setPresenceStatus("AVAILABLE");
                        navObservationRepository.save(restated);

                        ValidationIssue issue = new ValidationIssue(
                            "NAV_OBSERVATION",
                            restated.getId() != null ? restated.getId() : 0L,
                            "REVISION_DISCREPANCY",
                            String.format("Revision %d for %s on %s: value changed from %s to %s. Prior observation retained immutably.",
                                nextSeq, option.getAmfiCode(), rec.navDate(), latestExisting.getNavValue(), rec.navValue())
                        );
                        issue.setRevisionStatus("REVISED");
                        issue.setIntegrityCondition("CONFLICTING");
                        validationIssueRepository.save(issue);

                        revisions++;
                        issuesCreated++;
                    }
                }
            }
        }

        messages.add(String.format("Ingested %d observations (%d revisions, %d duplicates skipped) from artifact %d",
            ingested, revisions, duplicatesSkipped, artifact.getId()));

        return new IngestionSummary(
            artifact.getId(),
            artifact.getSha256Hash(),
            totalParsed,
            validRows,
            ingested,
            revisions,
            duplicatesSkipped,
            issuesCreated,
            messages
        );
    }
}
