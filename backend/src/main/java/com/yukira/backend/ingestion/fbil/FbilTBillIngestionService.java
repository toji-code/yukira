package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import com.yukira.backend.riskfree.RiskFreeGapInterval;
import com.yukira.backend.riskfree.RiskFreeHorizonCoverageDto;
import com.yukira.backend.riskfree.RiskFreeHorizonRecoveryResultDto;
import com.yukira.backend.riskfree.RiskFreeMultiHorizonCoverageDto;
import com.yukira.backend.service.ingestion.FbilDataFreshnessService;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class FbilTBillIngestionService {

    private final FbilSourceClient sourceClient;
    private final FbilTBillParser parser;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public FbilTBillIngestionService(
        FbilSourceClient sourceClient,
        FbilTBillParser parser,
        RiskFreeObservationRepository riskFreeObservationRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.sourceClient = sourceClient;
        this.parser = parser;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    public record FbilIngestionSummary(
        int totalParsed,
        int insertedCount,
        int skippedCount,
        int revisedCount,
        Long sourceArtifactId,
        String payloadSha256,
        String status,
        String message
    ) {
        public FbilIngestionSummary(int totalParsed, int insertedCount, int skippedCount, int revisedCount, Long sourceArtifactId, String payloadSha256) {
            this(totalParsed, insertedCount, skippedCount, revisedCount, sourceArtifactId, payloadSha256, "SUCCESS", null);
        }
    }

    /**
     * Incrementally refreshes FBIL 91-day Treasury Bill observations up to the given reference date (defaults to today).
     * If the database is already up to date, skips retrieval.
     * If the remote fetch fails (e.g. network timeout or portal maintenance), preserves existing data safely.
     */
    @Transactional
    public FbilIngestionSummary refreshLatestRates(java.time.LocalDate referenceDate) {
        final java.time.LocalDate targetDate = referenceDate != null ? referenceDate : java.time.LocalDate.now();
        var maxDateOpt = riskFreeObservationRepository.findMaxEffectiveDateByBenchmarkCode("FBIL_91D_TBILL");

        java.time.LocalDate startDate;
        if (maxDateOpt.isPresent()) {
            java.time.LocalDate maxDate = maxDateOpt.get();
            if (!maxDate.isBefore(targetDate)) {
                return new FbilIngestionSummary(
                    0, 0, 0, 0, null, null,
                    "UP_TO_DATE",
                    "FBIL 91D T-Bill already has observations up to " + maxDate + " (requested reference date: " + targetDate + ")."
                );
            }
            startDate = maxDate.plusDays(1);
        } else {
            startDate = java.time.LocalDate.of(2021, 1, 1);
        }

        try {
            SourceArtifact artifact = sourceClient.fetchAndPersistArtifact(startDate, targetDate);
            return ingestPayload("FBIL_91D_TBILL", artifact.getStorageUri(), artifact.getPayloadBlob());
        } catch (Exception e) {
            return new FbilIngestionSummary(
                0, 0, 0, 0, null, null,
                "FETCH_FAILED",
                "FBIL remote retrieval failed: " + e.getMessage() + ". Existing observations preserved."
            );
        }
    }

    @Transactional
    public FbilIngestionSummary ingestRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        SourceArtifact artifact = sourceClient.fetchAndPersistArtifact(startDate, endDate);
        return ingestPayload(artifact.getStorageUri(), artifact.getPayloadBlob());
    }

    /**
     * Ingests FBIL Treasury Bill payload bytes deterministically.
     * Guarantees idempotency, revision tracking, conflict detection, and immutable provenance.
     */
    @Transactional
    public FbilIngestionSummary ingestPayload(String sourceUri, byte[] payloadBytes) {
        return ingestPayload("FBIL_91D_TBILL", sourceUri, payloadBytes);
    }

    @Transactional
    public FbilIngestionSummary ingestPayload(String benchmarkCode, String sourceUri, byte[] payloadBytes) {
        if (sourceUri == null || sourceUri.isBlank()) {
            sourceUri = "https://www.fbil.org.in/money-market/tbill-curve";
        }
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        // 1. Persist or retrieve cryptographically immutable SourceArtifact
        SourceArtifact artifact = sourceClient.createOrGetSourceArtifact(sourceUri, payloadBytes);

        // 2. Deterministically parse records
        List<FbilTBillRecord> records = parser.parse(payloadBytes, benchmarkCode);

        int inserted = 0;
        int skipped = 0;
        int revised = 0;

        // 3. Process each record
        for (FbilTBillRecord record : records) {
            List<RiskFreeObservation> existingRevisions = riskFreeObservationRepository
                .findByBenchmarkCodeAndEffectiveDate(record.benchmarkCode(), record.effectiveDate());

            if (existingRevisions.isEmpty()) {
                // First observation for this date
                RiskFreeObservation obs = new RiskFreeObservation(
                    record.benchmarkCode(),
                    record.effectiveDate(),
                    record.quotedYield(),
                    record.daycountConvention(),
                    1,
                    record.availabilityTime()
                );
                obs.setSourceArtifact(artifact);
                obs.setQualityAssessment("VALID");
                obs.setVerificationStatus("VERIFIED");
                obs.setRevisionStatus("ORIGINAL");
                obs.setTemporalStatus("CURRENT");
                obs.setPresenceStatus("AVAILABLE");
                obs.setIntegrityCondition("NONE");

                riskFreeObservationRepository.save(obs);
                inserted++;
            } else {
                // Check if identical observation exists
                boolean exactMatch = existingRevisions.stream().anyMatch(
                    e -> e.getQuotedYield().compareTo(record.quotedYield()) == 0
                        && e.getAvailabilityTime().isEqual(record.availabilityTime())
                );

                if (exactMatch) {
                    skipped++;
                    continue;
                }

                // Check latest revision
                RiskFreeObservation latest = existingRevisions.stream()
                    .filter(RiskFreeObservation::getLatestRevision)
                    .findFirst()
                    .orElse(existingRevisions.get(existingRevisions.size() - 1));

                if (record.availabilityTime().isAfter(latest.getAvailabilityTime())) {
                    // Legitimate retroactive revision with later availability time
                    latest.setLatestRevision(false);
                    riskFreeObservationRepository.save(latest);

                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation revisedObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    revisedObs.setSourceArtifact(artifact);
                    revisedObs.setQualityAssessment("VALID");
                    revisedObs.setVerificationStatus("VERIFIED");
                    revisedObs.setRevisionStatus("REVISED");
                    revisedObs.setTemporalStatus("CURRENT");
                    revisedObs.setPresenceStatus("AVAILABLE");
                    revisedObs.setIntegrityCondition("NONE");

                    riskFreeObservationRepository.save(revisedObs);
                    revised++;
                } else if (record.availabilityTime().isEqual(latest.getAvailabilityTime())
                    && record.quotedYield().compareTo(latest.getQuotedYield()) != 0) {
                    // Conflicting quote published at the exact same availability instant
                    ValidationIssue issue = new ValidationIssue(
                        "RISK_FREE_BENCHMARK",
                        0L,
                        "INGESTION_CONFLICT",
                        String.format("Conflicting FBIL yield for %s on %s: existing=%s, new=%s at availability %s",
                            record.benchmarkCode(), record.effectiveDate(), latest.getQuotedYield(), record.quotedYield(), record.availabilityTime())
                    );
                    issue.setQualityAssessment("SUSPICIOUS");
                    issue.setIntegrityCondition("CONFLICTING");
                    validationIssueRepository.save(issue);

                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation conflictingObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    conflictingObs.setSourceArtifact(artifact);
                    conflictingObs.setQualityAssessment("SUSPICIOUS");
                    conflictingObs.setVerificationStatus("UNVERIFIED");
                    conflictingObs.setRevisionStatus("SUPERSEDED");
                    conflictingObs.setTemporalStatus("CURRENT");
                    conflictingObs.setPresenceStatus("AVAILABLE");
                    conflictingObs.setIntegrityCondition("CONFLICTING");

                    riskFreeObservationRepository.save(conflictingObs);
                    inserted++;
                } else {
                    // Observation with earlier availability time than existing latest
                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation priorObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    priorObs.setLatestRevision(false);
                    priorObs.setSourceArtifact(artifact);
                    priorObs.setQualityAssessment("VALID");
                    priorObs.setVerificationStatus("VERIFIED");
                    priorObs.setRevisionStatus("SUPERSEDED");
                    priorObs.setTemporalStatus("STALE");
                    priorObs.setPresenceStatus("AVAILABLE");
                    priorObs.setIntegrityCondition("NONE");

                    riskFreeObservationRepository.save(priorObs);
                    inserted++;
                }
            }
        }

        return new FbilIngestionSummary(
            records.size(),
            inserted,
            skipped,
            revised,
            artifact.getId(),
            artifact.getSha256Hash()
        );
    }

    // =========================================================================
    // Phase A5: Five-Year and Ten-Year FBIL Coverage & Bounded Recovery
    // =========================================================================

    private FbilDataFreshnessService freshnessService;

    @Autowired(required = false)
    public void setFreshnessService(FbilDataFreshnessService freshnessService) {
        this.freshnessService = freshnessService;
    }

    public static final String FBIL_91D_TBILL_CODE = "FBIL_91D_TBILL";
    public static final String FBIL_91D_TBILL_NAME = "FBIL 91-Day Treasury Bill Curve";
    public static final String FBIL_PROVIDER = "Financial Benchmarks India Pvt Ltd";
    public static final String DAYCOUNT_ACT_365 = "ACT_365";
    public static final String TENOR_91D = "91D";

    public static final String GOVERNANCE_DISCLAIMER =
        "Risk-free rate observations are ingested as authentic quoted yields from FBIL under the ACT/365 daycount convention. " +
        "Zero synthetic rates, forward fills, or zero-fills are generated. Multi-horizon coverage (3Y, 5Y, 10Y) does NOT authorize " +
        "unsupported multi-year risk-adjusted metrics or imply methodology approval under Phase 2H.";

    /**
     * Derives exact missing date intervals for FBIL risk-free rates within an evaluation window.
     */
    public List<RiskFreeGapInterval> deriveRiskFreeGapsInWindow(
        String benchmarkCode,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (windowStart == null || windowEnd == null || windowStart.isAfter(windowEnd)) {
            return List.of();
        }
        String code = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : FBIL_91D_TBILL_CODE;

        List<RiskFreeObservation> obsList = riskFreeObservationRepository
            .findByBenchmarkCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(code, windowStart, windowEnd)
            .stream()
            .filter(o -> Boolean.TRUE.equals(o.getLatestRevision()))
            .toList();

        if (obsList.isEmpty()) {
            return List.of(RiskFreeGapInterval.of(windowStart, windowEnd, "FULL_WINDOW_MISSING"));
        }

        List<RiskFreeGapInterval> gaps = new ArrayList<>();

        // 1. Leading gap before first observation
        LocalDate firstObsDate = obsList.getFirst().getEffectiveDate();
        if (ChronoUnit.DAYS.between(windowStart, firstObsDate) > 4) {
            gaps.add(RiskFreeGapInterval.of(windowStart, firstObsDate.minusDays(1), "LEADING_GAP"));
        }

        // 2. Intermediate gaps between observations
        for (int i = 0; i < obsList.size() - 1; i++) {
            LocalDate curr = obsList.get(i).getEffectiveDate();
            LocalDate next = obsList.get(i + 1).getEffectiveDate();
            long dayDiff = ChronoUnit.DAYS.between(curr, next);

            // Gaps > 4 days indicate missing clearing/business days (Friday to Monday is 3 days, long weekend is 4 days)
            if (dayDiff > 4) {
                gaps.add(RiskFreeGapInterval.of(curr.plusDays(1), next.minusDays(1), "INTERMEDIATE_GAP"));
            }
        }

        // 3. Trailing gap after last observation
        LocalDate lastObsDate = obsList.getLast().getEffectiveDate();
        if (ChronoUnit.DAYS.between(lastObsDate, windowEnd) > 3) {
            gaps.add(RiskFreeGapInterval.of(lastObsDate.plusDays(1), windowEnd, "TRAILING_GAP"));
        }

        return gaps;
    }

    /**
     * Evaluates factual risk-free rate coverage in a single horizon (3Y, 5Y, or 10Y).
     */
    @Transactional(readOnly = true)
    public RiskFreeHorizonCoverageDto evaluateRiskFreeHorizon(
        String benchmarkCode,
        AnalyticalHorizon horizon,
        LocalDate referenceDate
    ) {
        String code = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : FBIL_91D_TBILL_CODE;
        AnalyticalHorizon h = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate windowStart = h.calculateStartDate(refDate);
        LocalDate windowEnd = refDate;

        long distinctDates = riskFreeObservationRepository.countDistinctEffectiveDatesInWindow(code, windowStart, windowEnd);
        LocalDate earliestInWindow = riskFreeObservationRepository.findMinEffectiveDateInWindow(code, windowStart, windowEnd).orElse(null);
        LocalDate latestInWindow = riskFreeObservationRepository.findMaxEffectiveDateInWindow(code, windowStart, windowEnd).orElse(null);

        long allTimeCount = riskFreeObservationRepository.countByBenchmarkCode(code);
        LocalDate allTimeEarliest = riskFreeObservationRepository.findMinEffectiveDateByBenchmarkCode(code).orElse(null);
        LocalDate allTimeLatest = riskFreeObservationRepository.findMaxEffectiveDateByBenchmarkCode(code).orElse(null);

        List<RiskFreeGapInterval> missingIntervals = deriveRiskFreeGapsInWindow(code, windowStart, windowEnd);

        boolean hasLeading = missingIntervals.stream().anyMatch(g -> "LEADING_GAP".equals(g.gapClassification()) || "FULL_WINDOW_MISSING".equals(g.gapClassification()));
        boolean hasIntermediate = missingIntervals.stream().anyMatch(g -> "INTERMEDIATE_GAP".equals(g.gapClassification()));
        boolean hasTrailing = missingIntervals.stream().anyMatch(g -> "TRAILING_GAP".equals(g.gapClassification()));

        long spanDays = (earliestInWindow != null && latestInWindow != null)
            ? ChronoUnit.DAYS.between(earliestInWindow, latestInWindow) + 1
            : 0;

        String coverageStatus;
        if (distinctDates == 0) {
            coverageStatus = "NO_OBSERVATIONS";
        } else if (missingIntervals.isEmpty()) {
            coverageStatus = "FULL_COVERAGE";
        } else if (hasLeading && !hasIntermediate && !hasTrailing) {
            coverageStatus = "MISSING_LEADING_HISTORY";
        } else {
            coverageStatus = "PARTIAL_COVERAGE";
        }

        String freshnessStatus = "UNKNOWN";
        if (freshnessService != null) {
            var freshnessDto = freshnessService.getFreshnessStatus(refDate);
            freshnessStatus = freshnessDto.freshnessState();
        } else {
            if (allTimeLatest != null) {
                long diff = ChronoUnit.DAYS.between(allTimeLatest, refDate);
                if (diff <= 3) freshnessStatus = "FRESH";
                else if (diff <= 7) freshnessStatus = "PARTIAL";
                else freshnessStatus = "STALE";
            } else {
                freshnessStatus = "UNAVAILABLE";
            }
        }

        boolean isComplete = missingIntervals.isEmpty() && distinctDates > 0;
        String methodStatus = h.getMethodologyStatus();

        String explanation = buildHorizonExplanation(
            FBIL_91D_TBILL_NAME, h.getCode(), distinctDates, windowStart, windowEnd,
            earliestInWindow, latestInWindow, missingIntervals, coverageStatus, freshnessStatus
        );

        return new RiskFreeHorizonCoverageDto(
            code,
            FBIL_91D_TBILL_NAME,
            FBIL_PROVIDER,
            TENOR_91D,
            DAYCOUNT_ACT_365,
            h,
            h.getCode(),
            refDate,
            windowStart,
            windowEnd,
            distinctDates,
            earliestInWindow,
            latestInWindow,
            allTimeCount,
            allTimeEarliest,
            allTimeLatest,
            spanDays,
            missingIntervals.size(),
            missingIntervals,
            hasLeading,
            hasIntermediate,
            hasTrailing,
            coverageStatus,
            freshnessStatus,
            methodStatus,
            isComplete,
            explanation,
            GOVERNANCE_DISCLAIMER
        );
    }

    private String buildHorizonExplanation(
        String benchmarkName,
        String horizonCode,
        long distinctDates,
        LocalDate windowStart,
        LocalDate windowEnd,
        LocalDate earliestInWindow,
        LocalDate latestInWindow,
        List<RiskFreeGapInterval> missingIntervals,
        String coverageStatus,
        String freshnessStatus
    ) {
        if ("FULL_COVERAGE".equals(coverageStatus)) {
            return String.format("%s has %d distinct observation dates fully covering the %s horizon [%s to %s]. Source freshness is %s.",
                benchmarkName, distinctDates, horizonCode, windowStart, windowEnd, freshnessStatus);
        } else if ("MISSING_LEADING_HISTORY".equals(coverageStatus)) {
            RiskFreeGapInterval leading = missingIntervals.getFirst();
            return String.format("%s has %d observation dates in %s window, but lacks leading historical data from %s to %s (%d calendar days). Observations start on %s. Source freshness is %s.",
                benchmarkName, distinctDates, horizonCode, leading.startDate(), leading.endDate(), leading.missingCalendarDays(), earliestInWindow, freshnessStatus);
        } else if ("NO_OBSERVATIONS".equals(coverageStatus)) {
            return String.format("%s has zero observations recorded in the %s window [%s to %s]. Source freshness is %s.",
                benchmarkName, horizonCode, windowStart, windowEnd, freshnessStatus);
        } else {
            return String.format("%s has %d observation dates in %s window [%s to %s] with %d missing intervals. Coverage status is %s; source freshness is %s.",
                benchmarkName, distinctDates, horizonCode, windowStart, windowEnd, missingIntervals.size(), coverageStatus, freshnessStatus);
        }
    }

    /**
     * Evaluates multi-horizon coverage (3Y, 5Y, and 10Y) for FBIL 91-Day Treasury Bill series.
     */
    @Transactional(readOnly = true)
    public RiskFreeMultiHorizonCoverageDto evaluateRiskFreeMultiHorizon(String benchmarkCode, LocalDate referenceDate) {
        String code = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : FBIL_91D_TBILL_CODE;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();

        long allTimeCount = riskFreeObservationRepository.countByBenchmarkCode(code);
        LocalDate allTimeEarliest = riskFreeObservationRepository.findMinEffectiveDateByBenchmarkCode(code).orElse(null);
        LocalDate allTimeLatest = riskFreeObservationRepository.findMaxEffectiveDateByBenchmarkCode(code).orElse(null);

        RiskFreeHorizonCoverageDto cov3Y = evaluateRiskFreeHorizon(code, AnalyticalHorizon.THREE_YEAR, refDate);
        RiskFreeHorizonCoverageDto cov5Y = evaluateRiskFreeHorizon(code, AnalyticalHorizon.FIVE_YEAR, refDate);
        RiskFreeHorizonCoverageDto cov10Y = evaluateRiskFreeHorizon(code, AnalyticalHorizon.TEN_YEAR, refDate);

        return new RiskFreeMultiHorizonCoverageDto(
            code,
            FBIL_91D_TBILL_NAME,
            FBIL_PROVIDER,
            TENOR_91D,
            DAYCOUNT_ACT_365,
            refDate,
            allTimeCount,
            allTimeEarliest,
            allTimeLatest,
            cov3Y.freshnessStatus(),
            "APPROVED_SOURCE_CANDIDATE_HORIZONS",
            cov3Y,
            cov5Y,
            cov10Y,
            OffsetDateTime.now(),
            GOVERNANCE_DISCLAIMER
        );
    }

    /**
     * Executes bounded, chunked historical recovery for FBIL 91D T-Bill in a specific horizon.
     * Partitions missing intervals into bounded segments (max 365 days) and reuses stored artifacts where present.
     * Execution is isolated and resumable.
     */
    public RiskFreeHorizonRecoveryResultDto recoverRiskFreeHorizon(
        String benchmarkCode,
        AnalyticalHorizon horizon,
        LocalDate referenceDate,
        Integer maxChunks,
        Integer maxChunkDays,
        Boolean forceRefresh
    ) {
        String code = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : FBIL_91D_TBILL_CODE;
        AnalyticalHorizon h = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate windowStart = h.calculateStartDate(refDate);
        LocalDate windowEnd = refDate;

        int chunkLimit = (maxChunks != null && maxChunks > 0) ? maxChunks : 10;
        int chunkDayLimit = (maxChunkDays != null && maxChunkDays > 0) ? maxChunkDays : 365;
        boolean force = Boolean.TRUE.equals(forceRefresh);

        List<RiskFreeGapInterval> initialGaps = deriveRiskFreeGapsInWindow(code, windowStart, windowEnd);
        if (initialGaps.isEmpty()) {
            return new RiskFreeHorizonRecoveryResultDto(
                code, FBIL_91D_TBILL_NAME, FBIL_PROVIDER, DAYCOUNT_ACT_365,
                h.getCode(), refDate, windowStart, windowEnd,
                0, 0, 0, 0, 0, 0, 0, 0,
                "ALREADY_COVERED", List.of(), List.of(),
                "FBIL 91D T-Bill series has complete contiguous coverage for the requested horizon window.",
                GOVERNANCE_DISCLAIMER
            );
        }

        // Partition gaps into slices of <= chunkDayLimit
        record ChunkSlice(LocalDate start, LocalDate end, String gapType, int gapIndex) {}
        List<ChunkSlice> allChunks = new ArrayList<>();
        for (int i = 0; i < initialGaps.size(); i++) {
            RiskFreeGapInterval gap = initialGaps.get(i);
            LocalDate cur = gap.startDate();
            while (!cur.isAfter(gap.endDate())) {
                LocalDate cEnd = cur.plusDays(chunkDayLimit - 1);
                if (cEnd.isAfter(gap.endDate())) {
                    cEnd = gap.endDate();
                }
                allChunks.add(new ChunkSlice(cur, cEnd, gap.gapClassification(), i + 1));
                cur = cEnd.plusDays(1);
            }
        }

        int attempted = 0;
        int recovered = 0;
        int failed = 0;
        int inserted = 0;
        int skipped = 0;
        int revised = 0;
        List<RiskFreeHorizonRecoveryResultDto.IntervalRecoveryAudit> intervalAudits = new ArrayList<>();

        for (ChunkSlice chunk : allChunks) {
            if (attempted >= chunkLimit) {
                break;
            }
            attempted++;

            try {
                // Check if stored authentic artifact already exists
                Optional<SourceArtifact> storedArtifactOpt = (!force)
                    ? sourceClient.findStoredArtifact(chunk.start(), chunk.end())
                    : Optional.empty();

                SourceArtifact artifact;
                boolean reused = false;
                if (storedArtifactOpt.isPresent()) {
                    artifact = storedArtifactOpt.get();
                    reused = true;
                } else {
                    artifact = sourceClient.fetchAndPersistArtifact(chunk.start(), chunk.end(), force);
                }

                FbilIngestionSummary summary = ingestPayload(code, artifact.getStorageUri(), artifact.getPayloadBlob());

                inserted += summary.insertedCount();
                skipped += summary.skippedCount();
                revised += summary.revisedCount();

                boolean isSuccess = "SUCCESS".equalsIgnoreCase(summary.status()) || summary.totalParsed() > 0;
                if (isSuccess) {
                    recovered++;
                } else {
                    failed++;
                }

                intervalAudits.add(new RiskFreeHorizonRecoveryResultDto.IntervalRecoveryAudit(
                    chunk.start(), chunk.end(), attempted, artifact.getStorageUri(),
                    reused, artifact.getId(), summary.totalParsed(), summary.insertedCount(),
                    summary.skippedCount(), summary.status(), summary.message()
                ));

            } catch (Exception e) {
                failed++;
                intervalAudits.add(new RiskFreeHorizonRecoveryResultDto.IntervalRecoveryAudit(
                    chunk.start(), chunk.end(), attempted, null,
                    false, null, 0, 0, 0,
                    "FAILED", e.getMessage()
                ));
            }
        }

        // Recompute remaining gaps after chunk execution
        List<RiskFreeGapInterval> remainingGaps = deriveRiskFreeGapsInWindow(code, windowStart, windowEnd);

        String overallStatus;
        if (remainingGaps.isEmpty()) {
            overallStatus = "FULLY_RECOVERED";
        } else if (attempted >= chunkLimit && allChunks.size() > chunkLimit) {
            overallStatus = "LIMIT_REACHED";
        } else if (recovered > 0) {
            overallStatus = "PARTIALLY_RECOVERED";
        } else if (failed > 0) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "PARTIAL";
        }

        String msg = String.format("Attempted %d of %d recovery chunks (%d succeeded, %d failed). Inserted: %d, Skipped: %d, Revised: %d. Remaining gaps: %d.",
            attempted, allChunks.size(), recovered, failed, inserted, skipped, revised, remainingGaps.size());

        return new RiskFreeHorizonRecoveryResultDto(
            code, FBIL_91D_TBILL_NAME, FBIL_PROVIDER, DAYCOUNT_ACT_365,
            h.getCode(), refDate, windowStart, windowEnd,
            initialGaps.size(), attempted, recovered, failed,
            inserted, skipped, revised, remainingGaps.size(),
            overallStatus, remainingGaps, intervalAudits, msg, GOVERNANCE_DISCLAIMER
        );
    }
}
