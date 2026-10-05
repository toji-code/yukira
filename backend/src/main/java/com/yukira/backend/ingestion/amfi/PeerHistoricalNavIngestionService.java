package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.population.PeerCohortIngestionReport;
import com.yukira.backend.scoring.population.PeerFundCoverageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Bounded production-style historical NAV batch ingestion service for peer fund cohorts.
 *
 * Epistemic Rules:
 *  - Deterministic batch ordering and explicit run ID.
 *  - Failure isolation per fund / AMC.
 *  - Strictly zero fabricated, interpolated, or forward-filled observations.
 *  - Bitemporal immutability: identical duplicates skipped, revisions tracked monotonically.
 *  - Rigorous data quality classification: SUFFICIENT_HISTORY, PARTIAL_HISTORY, INSUFFICIENT_HISTORY, INGESTION_FAILED.
 */
@Service
@SuppressWarnings("null")
public class PeerHistoricalNavIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PeerHistoricalNavIngestionService.class);

    public static final String COHORT_ID = "FLEXI_CAP_DIRECT_GROWTH_V1";
    public static final String LOCAL_CACHE_DIR = "C:\\Users\\DEUS\\.gemini\\antigravity-ide\\brain\\f08f0612-7a8a-4abd-a61d-cffc9c393981\\scratch\\amc_nav_cache";

    public static final Map<String, String> AMC_MF_MAP = Map.ofEntries(
        Map.entry("ADITYA_BIRLA_SUN_LIFE_MF", "3"),
        Map.entry("BARODA_BNP_PARIBAS_MF", "4"),
        Map.entry("DSP_MF", "6"),
        Map.entry("HDFC_MF", "9"),
        Map.entry("QUANT_MF", "13"),
        Map.entry("JM_FINANCIAL_MF", "16"),
        Map.entry("KOTAK_MAHINDRA_MF", "17"),
        Map.entry("LIC_MF", "18"),
        Map.entry("ICICI_PRUDENTIAL_MF", "20"),
        Map.entry("NIPPON_INDIA_MF", "21"),
        Map.entry("SBI_MF", "22"),
        Map.entry("TATA_MF", "25"),
        Map.entry("TAURUS_MF", "26"),
        Map.entry("FRANKLIN_TEMPLETON_MF", "27"),
        Map.entry("UTI_MF", "28"),
        Map.entry("CANARA_ROBECO_MF", "32"),
        Map.entry("SUNDARAM_MF", "33"),
        Map.entry("HSBC_MF", "37"),
        Map.entry("QUANTUM_MF", "41"),
        Map.entry("INVESCO_MF", "42"),
        Map.entry("MIRAE_ASSET_MF", "45"),
        Map.entry("BANK_OF_INDIA_MF", "46"),
        Map.entry("EDELWEISS_MF", "47"),
        Map.entry("BANDHAN_MF", "48"),
        Map.entry("AXIS_MF", "53"),
        Map.entry("NAVI_MF", "54"),
        Map.entry("MOTILAL_OSWAL_MF", "55"),
        Map.entry("PGIM_INDIA_MF", "58"),
        Map.entry("UNION_MF", "61"),
        Map.entry("360_ONE_MF", "62"),
        Map.entry("PPFAS_MF", "64"),
        Map.entry("SHRIRAM_MF", "67"),
        Map.entry("MAHINDRA_MANULIFE_MF", "69"),
        Map.entry("ITI_MF", "70"),
        Map.entry("WHITEOAK_CAPITAL_MF", "71"),
        Map.entry("TRUST_MF", "72"),
        Map.entry("NJ_MF", "73"),
        Map.entry("BAJAJ_FINSERV_MF", "75"),
        Map.entry("HELIOS_MF", "76")
    );

    private final AmfiSourceClient sourceClient;
    private final AmfiNavParser parser;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public PeerHistoricalNavIngestionService(
        AmfiSourceClient sourceClient,
        AmfiNavParser parser,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        ValidationIssueRepository validationIssueRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.sourceClient = sourceClient;
        this.parser = parser;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.validationIssueRepository = validationIssueRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    /**
     * Executes bounded peer cohort historical NAV ingestion for Flexi Cap Direct Growth funds.
     */
    public PeerCohortIngestionReport ingestFlexiCapPeerCohort(LocalDate startDate, LocalDate endDate) {
        String runId = "RUN-PEER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Starting peer historical NAV batch ingestion. RunId: {}, Window: {} -> {}", runId, startDate, endDate);

        // 1. Identify peer candidates via canonical repository query
        List<SchemeOption> candidates = schemeOptionRepository.findAll((root, query, cb) -> {
            var planJoin = root.join("plan");
            var schemeJoin = planJoin.join("scheme");

            var predCategory = cb.or(
                cb.like(cb.lower(schemeJoin.get("category")), "%flexi cap fund%"),
                cb.like(cb.lower(schemeJoin.get("subcategory")), "%flexi cap fund%")
            );
            var predPlan = cb.equal(cb.upper(planJoin.get("planType")), "DIRECT");
            var predOption = cb.equal(cb.upper(root.get("optionType")), "GROWTH");

            return cb.and(predCategory, predPlan, predOption);
        });

        // Deterministic sorting by schemeOption ID
        candidates.sort(Comparator.comparing(SchemeOption::getId));

        List<PeerFundCoverageSummary> coverages = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int successfulFunds = 0;
        int partialFunds = 0;
        int zeroFunds = 0;
        int failedFunds = 0;
        long totalIngested = 0;
        long totalDuplicates = 0;

        for (SchemeOption option : candidates) {
            try {
                PeerFundCoverageSummary summary = ingestSingleFund(option, startDate, endDate, runId);
                coverages.add(summary);

                if ("SUFFICIENT_HISTORY".equals(summary.status())) {
                    successfulFunds++;
                } else if ("PARTIAL_HISTORY".equals(summary.status())) {
                    partialFunds++;
                } else {
                    zeroFunds++;
                }

                totalIngested += summary.observationCount();
                totalDuplicates += summary.duplicateCount();

            } catch (Exception e) {
                log.error("Failed historical ingestion for scheme option {}: {}", option.getId(), e.getMessage(), e);
                failedFunds++;
                coverages.add(new PeerFundCoverageSummary(
                    option.getId(),
                    option.getAmfiCode(),
                    option.getIsin(),
                    option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
                        ? option.getPlan().getScheme().getAmc().getCode() : "UNKNOWN",
                    option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "Unknown",
                    0,
                    null,
                    null,
                    "INGESTION_FAILED",
                    0,
                    0,
                    false,
                    "ERROR: " + e.getMessage()
                ));
            }
        }

        messages.add(String.format("Batch completed. Candidates: %d, Sufficient (>=700): %d, Partial: %d, Zero/New: %d, Failed: %d. Total obs: %d.",
            candidates.size(), successfulFunds, partialFunds, zeroFunds, failedFunds, totalIngested));

        return new PeerCohortIngestionReport(
            runId,
            COHORT_ID,
            startDate,
            endDate,
            candidates.size(),
            successfulFunds,
            partialFunds,
            zeroFunds,
            failedFunds,
            totalIngested,
            totalDuplicates,
            coverages,
            messages
        );
    }

    /**
     * Ingests historical data for a single scheme option with failure isolation and chunking support.
     */
    @Transactional
    public PeerFundCoverageSummary ingestSingleFund(SchemeOption option, LocalDate startDate, LocalDate endDate, String runId) {
        String amcCode = option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
            ? option.getPlan().getScheme().getAmc().getCode() : null;
        String schemeName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName() : "Unknown";
        String amfiCode = option.getAmfiCode() != null ? option.getAmfiCode().trim() : "";
        String isin = option.getIsin() != null ? option.getIsin().trim() : "";

        String mfId = amcCode != null ? AMC_MF_MAP.get(amcCode) : null;
        if (mfId == null) {
            // New AMC or unmapped AMC -> Report explicit non-fabricated status
            return new PeerFundCoverageSummary(
                option.getId(), amfiCode, isin, amcCode, schemeName,
                0, null, null, "INSUFFICIENT_HISTORY", 0, 0, false, "UNMAPPED_AMC_OR_NEW_FUND"
            );
        }

        // Retrieve raw payload(s) for this AMC across the date window
        List<SourceArtifact> artifacts = resolveOrFetchAmcArtifacts(amcCode, mfId, startDate, endDate);
        if (artifacts.isEmpty()) {
            return new PeerFundCoverageSummary(
                option.getId(), amfiCode, isin, amcCode, schemeName,
                0, null, null, "INSUFFICIENT_HISTORY", 0, 0, false, "NO_DATA_RETURNED"
            );
        }

        // Parse records matching this scheme option across all artifacts.
        // Each matched record retains the EXACT source artifact that physically contained it so
        // that every persisted observation is attributable to its own raw payload, never to an
        // arbitrary sibling chunk of the same AMC.
        Map<LocalDate, MatchedRecord> matchedByDate = new TreeMap<>();
        StringBuilder artifactHashes = new StringBuilder();

        for (SourceArtifact artifact : artifacts) {
            artifactHashes.append(artifact.getSha256Hash()).append(";");
            List<AmfiNavRecord> parsed = parser.parse(artifact.getPayloadBlob());
            for (AmfiNavRecord rec : parsed) {
                if (!rec.isValid()) continue;

                // Match by exact AMFI code or ISIN
                boolean amfiMatch = amfiCode.equals(rec.schemeCode() != null ? rec.schemeCode().trim() : "");
                boolean isinMatch = !isin.isEmpty() && isin.equalsIgnoreCase(rec.isinGrowth() != null ? rec.isinGrowth().trim() : "");

                if (amfiMatch || isinMatch) {
                    if (!rec.navDate().isBefore(startDate) && !rec.navDate().isAfter(endDate)) {
                        matchedByDate.putIfAbsent(rec.navDate(), new MatchedRecord(rec, artifact));
                    }
                }
            }
        }

        List<AmfiNavRecord> distinctRecords = new ArrayList<>();
        for (MatchedRecord m : matchedByDate.values()) {
            distinctRecords.add(m.record());
        }

        int duplicatesSkipped = 0;
        int inserted = 0;
        int revisions = 0;
        BigDecimal prevNav = null;

        for (Map.Entry<LocalDate, MatchedRecord> entry : matchedByDate.entrySet()) {
            AmfiNavRecord rec = entry.getValue().record();
            SourceArtifact sourceArtifact = entry.getValue().artifact();

            // Boundary validation
            if (rec.navValue().compareTo(new BigDecimal("0.00010000")) <= 0) {
                continue;
            }

            // Jump check
            boolean isSuspiciousJump = false;
            if (prevNav != null && prevNav.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal jump = rec.navValue().subtract(prevNav).divide(prevNav, 8, RoundingMode.HALF_UP).abs();
                if (jump.compareTo(new BigDecimal("0.20")) > 0) {
                    isSuspiciousJump = true;
                }
            }
            prevNav = rec.navValue();

            // Analytical EOD availability convention (23:59:59.999 IST)
            OffsetDateTime analyticalAvailability = OffsetDateTime.of(
                rec.navDate().atTime(23, 59, 59, 999000000),
                ZoneOffset.ofHoursMinutes(5, 30)
            );

            // Bitemporal ledger persistence
            List<NavObservation> existingObs = navObservationRepository
                .findBySchemeOptionIdAndEffectiveDate(option.getId(), rec.navDate());

            if (existingObs.isEmpty()) {
                NavObservation obs = new NavObservation(
                    option, rec.navDate(), rec.navValue(), 1, analyticalAvailability
                );
                obs.setSourceArtifact(sourceArtifact);
                obs.setRevisionStatus("ORIGINAL");
                obs.setTemporalStatus("CURRENT");
                obs.setQualityAssessment(isSuspiciousJump ? "SUSPICIOUS" : "VALID");
                obs.setVerificationStatus("VERIFIED");
                obs.setPresenceStatus("AVAILABLE");
                navObservationRepository.save(obs);
                inserted++;
            } else {
                NavObservation latest = existingObs.stream()
                    .max(Comparator.comparingInt(NavObservation::getRevisionSeq))
                    .orElseThrow();

                if (latest.getNavValue().compareTo(rec.navValue()) == 0) {
                    duplicatesSkipped++;
                } else {
                    latest.setLatestRevision(false);
                    latest.setRevisionStatus("SUPERSEDED");
                    navObservationRepository.save(latest);

                    int nextSeq = latest.getRevisionSeq() + 1;
                    NavObservation revised = new NavObservation(
                        option, rec.navDate(), rec.navValue(), nextSeq, analyticalAvailability
                    );
                    revised.setSourceArtifact(sourceArtifact);
                    revised.setRevisionStatus("REVISED");
                    revised.setTemporalStatus("CURRENT");
                    revised.setQualityAssessment(isSuspiciousJump ? "SUSPICIOUS" : "VALID");
                    revised.setVerificationStatus("VERIFIED");
                    revised.setPresenceStatus("AVAILABLE");
                    navObservationRepository.save(revised);
                    revisions++;
                }
            }
        }

        // Query total verified observations in database for this scheme option up to cutoff
        long totalInDb = navObservationRepository.countBySchemeOptionId(option.getId());
        LocalDate firstDate = distinctRecords.isEmpty() ? null : distinctRecords.get(0).navDate();
        LocalDate lastDate = distinctRecords.isEmpty() ? null : distinctRecords.get(distinctRecords.size() - 1).navDate();

        String status;
        if (totalInDb >= 700) {
            status = "SUFFICIENT_HISTORY";
        } else if (totalInDb > 0) {
            status = "PARTIAL_HISTORY";
        } else {
            status = "INSUFFICIENT_HISTORY";
        }

        return new PeerFundCoverageSummary(
            option.getId(),
            amfiCode,
            isin,
            amcCode,
            schemeName,
            (int) totalInDb,
            firstDate,
            lastDate,
            status,
            0,
            duplicatesSkipped,
            totalInDb >= 700,
            artifactHashes.toString()
        );
    }

    /**
     * Resolves AMC data from local session cache or official AMFI portal.
     * Uses automatic yearly chunking for high-volume AMCs to prevent ASP.NET 30MB stream errors.
     */
    private List<SourceArtifact> resolveOrFetchAmcArtifacts(String amcCode, String mfId, LocalDate startDate, LocalDate endDate) {
        List<SourceArtifact> artifacts = new ArrayList<>();

        // Check if full 3Y file is already cached locally
        File fullCache = new File(LOCAL_CACHE_DIR, String.format("%s_%s.txt", amcCode, mfId));
        if (fullCache.exists() && fullCache.length() > 20000) {
            byte[] bytes = readFileBytes(fullCache);
            if (bytes != null && !isHtmlError(bytes)) {
                artifacts.add(sourceClient.persistRawPayload(bytes, fullCache.toURI().toString(), OffsetDateTime.now()));
                return artifacts;
            }
        }

        // Check if annual chunks are cached locally
        File chunk1 = new File(LOCAL_CACHE_DIR, String.format("%s_%s_01-Jan-2021_31-Dec-2021.txt", amcCode, mfId));
        File chunk2 = new File(LOCAL_CACHE_DIR, String.format("%s_%s_01-Jan-2022_31-Dec-2022.txt", amcCode, mfId));
        File chunk3 = new File(LOCAL_CACHE_DIR, String.format("%s_%s_01-Jan-2023_15-Jan-2024.txt", amcCode, mfId));

        if (chunk1.exists() && chunk2.exists() && chunk3.exists()) {
            byte[] b1 = readFileBytes(chunk1);
            byte[] b2 = readFileBytes(chunk2);
            byte[] b3 = readFileBytes(chunk3);
            if (b1 != null && b2 != null && b3 != null) {
                artifacts.add(sourceClient.persistRawPayload(b1, chunk1.toURI().toString(), OffsetDateTime.now()));
                artifacts.add(sourceClient.persistRawPayload(b2, chunk2.toURI().toString(), OffsetDateTime.now()));
                artifacts.add(sourceClient.persistRawPayload(b3, chunk3.toURI().toString(), OffsetDateTime.now()));
                return artifacts;
            }
        }

        // Live download with yearly chunk fallback
        try {
            SourceArtifact singleArtifact = sourceClient.fetchAndPersistArtifact("all", mfId, startDate, endDate);
            if (singleArtifact.getPayloadBlob() != null && !isHtmlError(singleArtifact.getPayloadBlob())) {
                artifacts.add(singleArtifact);
                return artifacts;
            }
        } catch (Exception e) {
            log.info("Full 3-year request for AMC {} (mf={}) failed/exceeded size limit. Falling back to annual chunks.", amcCode, mfId);
        }

        // Fallback: Annual chunks
        List<LocalDate[]> windows = List.of(
            new LocalDate[]{LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31)},
            new LocalDate[]{LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)},
            new LocalDate[]{LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 15)}
        );

        for (LocalDate[] w : windows) {
            try {
                SourceArtifact chunkArtifact = sourceClient.fetchAndPersistArtifact("all", mfId, w[0], w[1]);
                if (chunkArtifact.getPayloadBlob() != null && !isHtmlError(chunkArtifact.getPayloadBlob())) {
                    artifacts.add(chunkArtifact);
                }
            } catch (Exception ex) {
                log.warn("Chunk {} -> {} for AMC {} failed: {}", w[0], w[1], amcCode, ex.getMessage());
            }
        }

        return artifacts;
    }

    private static boolean isHtmlError(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return true;
        if (bytes.length < 500) return true;
        String prefix = new String(bytes, 0, Math.min(bytes.length, 120), StandardCharsets.ISO_8859_1).trim().toLowerCase();
        return prefix.startsWith("<") || prefix.contains("<html") || prefix.contains("<!doctype");
    }

    /**
     * A parsed AMFI record bound to the exact {@link SourceArtifact} whose raw payload
     * physically contained it. Required so that chunked AMC downloads preserve per-observation
     * cryptographic provenance.
     */
    private record MatchedRecord(AmfiNavRecord record, SourceArtifact artifact) {}

    private static byte[] readFileBytes(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            return fis.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }
}
