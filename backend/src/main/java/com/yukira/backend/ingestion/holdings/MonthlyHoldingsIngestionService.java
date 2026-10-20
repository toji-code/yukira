package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service orchestrating automated monthly fund holdings ingestion.
 *
 * Epistemic Rules:
 * 1. Provenance-aware: every snapshot is tied to a cryptographic SourceArtifact (SHA-256 digest).
 * 2. Idempotent & Bitemporal: duplicate artifacts for identical portfolio dates are deduplicated; restatements increment revision_seq.
 * 3. Deterministic Identity Resolution: ISIN exact match prioritized over canonical name matching.
 * 4. Data Quality Taxonomy: logs deterministic validation issues without fabricating numbers or dropping optional fields.
 * 5. Analytical Separation: holdings ingestion operates independently from quantitative fund scoring.
 */
@Service
@SuppressWarnings("null")
public class MonthlyHoldingsIngestionService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsIngestionService.class);

    private final MonthlyHoldingsParserRegistry parserRegistry;
    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityRepository securityRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public MonthlyHoldingsIngestionService(
        MonthlyHoldingsParserRegistry parserRegistry,
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityRepository securityRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.parserRegistry = parserRegistry;
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityRepository = securityRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    /**
     * Ingests a monthly portfolio disclosure CSV payload for a target scheme option.
     */
    @Transactional
    public HoldingsIngestionSummary ingestMonthlyHoldings(
        Long targetSchemeOptionId,
        LocalDate fallbackPortfolioDate,
        byte[] payloadBytes,
        String sourceUri
    ) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("Monthly holdings payload bytes cannot be empty");
        }

        SchemeOption targetOption = schemeOptionRepository.findById(targetSchemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Target SchemeOption #" + targetSchemeOptionId + " not found"));

        String sha256 = computeSha256(payloadBytes);
        OffsetDateTime retrievalTime = OffsetDateTime.now();

        DataSource amcSource = dataSourceRepository.findByCode("SEBI_AMC_DISCLOSURE")
            .orElseGet(() -> dataSourceRepository.save(new DataSource(
                "SEBI_AMC_DISCLOSURE",
                "SEBI Monthly Portfolio Disclosure Feed",
                "https://www.amfiindia.com"
            )));

        SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(sha256)
            .orElseGet(() -> {
                SourceArtifact sa = new SourceArtifact(amcSource, retrievalTime, "PORTFOLIO_DISCLOSURE_CSV", sha256, (long) payloadBytes.length);
                sa.setStorageUri(sourceUri != null ? sourceUri : "SEBI_MONTHLY_PORTFOLIO_DISCLOSURE");
                sa.setPayloadBlob(payloadBytes);
                return sourceArtifactRepository.save(sa);
            });

        if (artifact.getPayloadBlob() == null) {
            artifact.setPayloadBlob(payloadBytes);
            artifact = sourceArtifactRepository.save(artifact);
        }

        return processArtifactIngestion(targetOption, fallbackPortfolioDate, artifact, payloadBytes, null);
    }

    /**
     * Ingests monthly holdings from an existing persisted SourceArtifact.
     */
    @Transactional
    public HoldingsIngestionSummary ingestArtifact(Long targetSchemeOptionId, LocalDate fallbackPortfolioDate, SourceArtifact artifact) {
        return ingestArtifact(targetSchemeOptionId, fallbackPortfolioDate, artifact, null);
    }

    @Transactional
    public HoldingsIngestionSummary ingestArtifact(
        Long targetSchemeOptionId,
        LocalDate fallbackPortfolioDate,
        SourceArtifact artifact,
        MonthlyHoldingsSourceDescriptor explicitDescriptor
    ) {
        if (artifact == null || artifact.getPayloadBlob() == null) {
            throw new IllegalArgumentException("SourceArtifact or payload blob cannot be null");
        }
        SchemeOption targetOption = schemeOptionRepository.findById(targetSchemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Target SchemeOption #" + targetSchemeOptionId + " not found"));

        return processArtifactIngestion(targetOption, fallbackPortfolioDate, artifact, artifact.getPayloadBlob(), explicitDescriptor);
    }

    private HoldingsIngestionSummary processArtifactIngestion(
        SchemeOption targetOption,
        LocalDate fallbackPortfolioDate,
        SourceArtifact artifact,
        byte[] payloadBytes,
        MonthlyHoldingsSourceDescriptor explicitDescriptor
    ) {
        String amcCode = targetOption.getPlan() != null && targetOption.getPlan().getScheme() != null && targetOption.getPlan().getScheme().getAmc() != null
            ? targetOption.getPlan().getScheme().getAmc().getCode() : "AMC";
        String schemeCode = targetOption.getPlan() != null && targetOption.getPlan().getScheme() != null
            ? targetOption.getPlan().getScheme().getCode() : "SCHEME";
        String schemeName = targetOption.getPlan() != null && targetOption.getPlan().getScheme() != null
            ? targetOption.getPlan().getScheme().getName() : "Scheme";

        String storageUri = artifact != null && artifact.getStorageUri() != null && !artifact.getStorageUri().isBlank()
            ? artifact.getStorageUri() : "https://portal.amfiindia.com";

        MonthlyHoldingsSourceDescriptor descriptor = explicitDescriptor != null ? explicitDescriptor : MonthlyHoldingsSourceDescriptor.of(
            amcCode, amcCode, schemeCode, schemeName, targetOption.getAmfiCode(), targetOption.getIsin(), storageUri, fallbackPortfolioDate
        );

        MonthlyHoldingsParser parser = parserRegistry.resolveParser(descriptor, payloadBytes);
        PpfasXlsxHoldingsParser.ParseResult parseResult = parser.parse(payloadBytes, descriptor);

        List<MonthlyHoldingsRecord> parsedRecords = parseResult.records();
        LocalDate determinedPortfolioDate = parseResult.portfolioDate() != null ? parseResult.portfolioDate() : fallbackPortfolioDate;

        List<String> messages = new ArrayList<>();

        int totalParsed = parsedRecords.size();
        int validRows = 0;
        int issuesCreated = 0;

        List<MonthlyHoldingsRecord> validRecords = new ArrayList<>();

        for (MonthlyHoldingsRecord rec : parsedRecords) {
            if (!rec.isValid()) {
                ValidationIssue issue = new ValidationIssue(
                    "SOURCE_ARTIFACT",
                    artifact.getId(),
                    "HOLDINGS_MALFORMED_ROW",
                    String.format("Line %d: %s [raw: %s]", rec.lineNumber(), rec.parseErrorMessage(), rec.rawLine())
                );
                issue.setQualityAssessment("INVALID");
                issue.setVerificationStatus("UNVERIFIED");
                validationIssueRepository.save(issue);
                issuesCreated++;
                continue;
            }

            validRows++;
            validRecords.add(rec);

            if (determinedPortfolioDate == null && rec.portfolioDate() != null) {
                determinedPortfolioDate = rec.portfolioDate();
            }
        }

        if (determinedPortfolioDate == null) {
            determinedPortfolioDate = LocalDate.now().withDayOfMonth(1).minusDays(1); // Default to last day of previous month
            messages.add("Reporting date omitted in payload; defaulted to " + determinedPortfolioDate);
        }

        final LocalDate portfolioDate = determinedPortfolioDate;

        // Check for existing snapshot for this option and portfolio date
        Optional<PortfolioSnapshot> existingSnapshotOpt = snapshotRepository.findBySchemeOptionIdAndPortfolioDate(targetOption.getId(), portfolioDate);
        int revisionSeq = 1;

        if (existingSnapshotOpt.isPresent()) {
            PortfolioSnapshot existing = existingSnapshotOpt.get();
            if (existing.getSourceArtifact() != null && artifact.getSha256Hash().equals(existing.getSourceArtifact().getSha256Hash())) {
                messages.add(String.format("Identical holdings snapshot already ingested for date %s (Artifact #%d); skipping redundant parse.", portfolioDate, artifact.getId()));
                return new HoldingsIngestionSummary(
                    artifact.getId(), artifact.getSha256Hash(), targetOption.getId(), portfolioDate,
                    totalParsed, validRows, 0,
                    0, issuesCreated, existing.getSumReportedWeights(), messages
                );
            }

            // Restatement: Supersede existing snapshot
            existing.setLatestRevision(false);
            snapshotRepository.save(existing);
            revisionSeq = existing.getRevisionSeq() + 1;
            messages.add(String.format("Restatement detected for portfolio date %s: creating revision #%d", portfolioDate, revisionSeq));
        }

        // Validate individual holdings metrics & compute total weight
        BigDecimal sumWeights = BigDecimal.ZERO;
        Set<String> seenIsins = new HashSet<>();
        List<PortfolioHolding> holdingsToSave = new ArrayList<>();
        int rank = 1;

        // Sort holdings by weight descending
        validRecords.sort((a, b) -> {
            if (a.weight() == null && b.weight() == null) return 0;
            if (a.weight() == null) return 1;
            if (b.weight() == null) return -1;
            return b.weight().compareTo(a.weight());
        });

        for (MonthlyHoldingsRecord rec : validRecords) {
            // Validation 1: Negative weight or market value
            if (rec.weight() != null && rec.weight().compareTo(BigDecimal.ZERO) < 0) {
                ValidationIssue issue = new ValidationIssue(
                    "SCHEME_OPTION", targetOption.getId(), "NEGATIVE_HOLDING_WEIGHT",
                    String.format("Line %d: Negative weight %s for security '%s'", rec.lineNumber(), rec.weight(), rec.securityName())
                );
                issue.setQualityAssessment("INVALID");
                validationIssueRepository.save(issue);
                issuesCreated++;
                continue;
            }

            // Validation 2: Weight > 100%
            if (rec.weight() != null && rec.weight().compareTo(BigDecimal.ONE) > 0) {
                ValidationIssue issue = new ValidationIssue(
                    "SCHEME_OPTION", targetOption.getId(), "EXCESSIVE_HOLDING_WEIGHT",
                    String.format("Line %d: Weight %s exceeds 100%% for security '%s'", rec.lineNumber(), rec.weight(), rec.securityName())
                );
                issue.setQualityAssessment("SUSPICIOUS");
                validationIssueRepository.save(issue);
                issuesCreated++;
            }

            // Validation 3: Duplicate ISIN within snapshot
            if (rec.isin() != null && !seenIsins.add(rec.isin())) {
                ValidationIssue issue = new ValidationIssue(
                    "SCHEME_OPTION", targetOption.getId(), "DUPLICATE_HOLDING_ISIN",
                    String.format("Line %d: Duplicate ISIN '%s' for security '%s' in snapshot date %s", rec.lineNumber(), rec.isin(), rec.securityName(), portfolioDate)
                );
                issue.setQualityAssessment("SUSPICIOUS");
                validationIssueRepository.save(issue);
                issuesCreated++;
            }


            if (rec.weight() != null) {
                sumWeights = sumWeights.add(rec.weight());
            }

            // Identity Resolution:
            Security security = resolveOrCreateSecurity(rec.securityName(), rec.isin(), rec.sector(), rec.assetClass());

            PortfolioHolding holding = new PortfolioHolding();
            holding.setSecurity(security);
            holding.setReportedWeight(rec.weight() != null ? rec.weight() : BigDecimal.ZERO);
            holding.setMarketValue(rec.marketValue());
            holding.setQuantity(rec.quantity());
            holding.setHoldingRank(rank++);
            holdingsToSave.add(holding);
        }

        // Create & Save PortfolioSnapshot with reporting date hierarchy safety
        boolean hasNewerSnapshot = snapshotRepository.existsBySchemeOptionIdAndPortfolioDateAfter(targetOption.getId(), portfolioDate);
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setSchemeOption(targetOption);
        snapshot.setPortfolioDate(portfolioDate);
        snapshot.setRevisionSeq(revisionSeq);
        snapshot.setLatestRevision(!hasNewerSnapshot);
        if (hasNewerSnapshot) {
            messages.add(String.format("Historical snapshot for portfolio date %s ingested as non-latest revision (newer snapshot exists).", portfolioDate));
        }
        snapshot.setAvailabilityTime(descriptor.resolvedAvailabilityTime(portfolioDate));
        snapshot.setReportedHoldingsCount(holdingsToSave.size());
        snapshot.setSumReportedWeights(sumWeights);
        snapshot.setSourceArtifact(artifact);
        PortfolioSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        // Link holdings to snapshot & save
        for (PortfolioHolding h : holdingsToSave) {
            h.setPortfolioSnapshot(savedSnapshot);
        }
        holdingRepository.saveAll(holdingsToSave);

        messages.add(String.format("Successfully ingested %d holdings for %s (Date: %s, Total Weight: %s)",
            holdingsToSave.size(), targetOption.getPlan().getScheme().getName(), portfolioDate, sumWeights));

        log.info("Monthly holdings ingestion completed for option #{}: {} holdings, sumWeights={}, revisionSeq={}",
            targetOption.getId(), holdingsToSave.size(), sumWeights, revisionSeq);

        return new HoldingsIngestionSummary(
            artifact.getId(),
            artifact.getSha256Hash(),
            targetOption.getId(),
            portfolioDate,
            totalParsed,
            validRows,
            holdingsToSave.size(),
            revisionSeq > 1 ? 1 : 0,
            issuesCreated,
            sumWeights,
            messages
        );
    }

    private Security resolveOrCreateSecurity(String securityName, String isin, String sector, String assetClass) {
        // 1. Exact match by ISIN in SecurityIdentifierRepository
        if (isin != null && !isin.isBlank()) {
            Optional<SecurityIdentifier> siOpt = securityIdentifierRepository.findTopByIdTypeAndIdValueOrderByIdAsc("ISIN", isin.toUpperCase(Locale.ROOT));
            if (siOpt.isPresent()) {
                return siOpt.get().getSecurity();
            }
        }

        // 2. Match by canonical name in SecurityRepository
        Optional<Security> byNameOpt = securityRepository.findTopByCanonicalNameOrderByIdAsc(securityName);
        if (byNameOpt.isPresent()) {
            Security sec = byNameOpt.get();
            Optional<SecurityIdentifier> existingIsin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN");

            // If existing security has no ISIN registered yet, and we have an ISIN, attach it.
            if (isin != null && !isin.isBlank() && existingIsin.isEmpty()) {
                SecurityIdentifier si = new SecurityIdentifier(sec, "ISIN", isin.toUpperCase(Locale.ROOT), LocalDate.of(2000, 1, 1));
                securityIdentifierRepository.save(si);
                return sec;
            }

            // If existing security has the SAME ISIN or no ISIN in request, return matched security.
            if (isin == null || isin.isBlank() || (existingIsin.isPresent() && existingIsin.get().getIdValue().equalsIgnoreCase(isin))) {
                return sec;
            }

            // If name matches BUT existing security has a DIFFERENT ISIN, do NOT merge. Fall through to create distinct security.
        }

        // 3. Create new Security & ISIN identifier if present
        Security newSec = new Security();
        newSec.setCanonicalName(securityName);
        newSec.setAssetClass(assetClass != null ? assetClass : "Equity");
        newSec.setInstrumentType("EQUITY_SHARES");
        newSec.setIssuerName(securityName);
        newSec.setSector(sector != null ? sector : "Other");
        newSec.setStatus("ACTIVE");
        Security savedSec = securityRepository.save(newSec);

        if (isin != null && !isin.isBlank()) {
            SecurityIdentifier si = new SecurityIdentifier(savedSec, "ISIN", isin.toUpperCase(Locale.ROOT), LocalDate.of(2000, 1, 1));
            securityIdentifierRepository.save(si);
        }

        return savedSec;
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
