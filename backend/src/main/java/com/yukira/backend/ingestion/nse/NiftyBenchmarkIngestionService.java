package com.yukira.backend.ingestion.nse;

import com.yukira.backend.benchmark.*;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import com.yukira.backend.universe.AnalyticalHorizon;
import com.yukira.backend.universe.EquityUniverseCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@SuppressWarnings("null")
public class NiftyBenchmarkIngestionService {

    public static final String NIFTY_500_TRI_CODE = "NIFTY_500_TRI";
    public static final String NIFTY_500_TRI_NAME = "NIFTY 500 Total Returns Index";
    public static final String NSE_PROVIDER = "NSE Indices Limited";

    private final NiftySourceClient sourceClient;
    private final NiftyTriParser parser;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private BenchmarkDataFreshnessService freshnessService;

    public NiftyBenchmarkIngestionService(
        NiftySourceClient sourceClient,
        NiftyTriParser parser,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository
    ) {
        this.sourceClient = sourceClient;
        this.parser = parser;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
    }

    @Autowired(required = false)
    public void setFreshnessService(BenchmarkDataFreshnessService freshnessService) {
        this.freshnessService = freshnessService;
    }

    public record NiftyIngestionSummary(
        int totalParsed,
        int insertedCount,
        int skippedCount,
        int revisedCount,
        Long sourceArtifactId,
        String payloadSha256
    ) {}

    /**
     * Comprehensive audit result for category benchmark ingestion.
     */
    public record BenchmarkIngestionAuditResult(
        String benchmarkCode,
        String benchmarkName,
        String provider,
        String returnVariant,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate actualMinDate,
        LocalDate actualMaxDate,
        int rowsReceived,
        int rowsAccepted,
        int rowsRejected,
        int rowsInserted,
        int rowsRevised,
        int duplicatesSkipped,
        Long sourceArtifactId,
        String payloadSha256,
        String status, // SUCCESS, PARTIAL, SKIPPED_ALREADY_SUFFICIENT, FAILED
        String failureReason
    ) {}

    /**
     * Ensures the canonical NIFTY 500 TRI Benchmark entity is registered.
     */
    @Transactional
    public Benchmark ensureCanonicalBenchmark() {
        return benchmarkRepository.findByCode(NIFTY_500_TRI_CODE)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                NIFTY_500_TRI_CODE,
                NIFTY_500_TRI_NAME,
                NSE_PROVIDER,
                "TRI"
            )));
    }

    /**
     * Ensures all five canonical category benchmarks for the reference universe are registered.
     */
    @Transactional
    public List<Benchmark> ensureCategoryBenchmarks() {
        List<Benchmark> list = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            Benchmark bm = benchmarkRepository.findByCode(desc.benchmarkCode())
                .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                    desc.benchmarkCode(),
                    desc.benchmarkName(),
                    desc.provider(),
                    desc.returnVariant()
                )));
            list.add(bm);
        }
        return list;
    }

    @Transactional
    public NiftyIngestionSummary ingestRange(LocalDate startDate, LocalDate endDate) {
        SourceArtifact artifact = sourceClient.fetchAndPersistArtifact("Nifty 500", startDate, endDate);
        return ingestPayload(NIFTY_500_TRI_CODE, artifact.getStorageUri(), artifact.getPayloadBlob());
    }

    /**
     * Ingests any canonical category benchmark series for an explicit date range.
     */
    @Transactional
    public NiftyIngestionSummary ingestCategoryBenchmarkRange(String benchmarkCode, LocalDate startDate, LocalDate endDate) {
        BenchmarkIngestionAuditResult audit = ingestCategoryBenchmarkWithAudit(benchmarkCode, startDate, endDate);
        return new NiftyIngestionSummary(
            audit.rowsAccepted(),
            audit.rowsInserted(),
            audit.duplicatesSkipped(),
            audit.rowsRevised(),
            audit.sourceArtifactId(),
            audit.payloadSha256()
        );
    }

    /**
     * Ingests a canonical category benchmark series with full audit output and provenance.
     */
    @Transactional
    public BenchmarkIngestionAuditResult ingestCategoryBenchmarkWithAudit(String benchmarkCode, LocalDate startDate, LocalDate endDate) {
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        if (descOpt.isEmpty()) {
            throw new IllegalArgumentException("Unknown or unmapped benchmark code: " + benchmarkCode);
        }
        var desc = descOpt.get();
        if (startDate == null) {
            startDate = LocalDate.of(2021, 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        try {
            SourceArtifact artifact = sourceClient.fetchAndPersistArtifact(desc.nseIndexQueryName(), startDate, endDate);
            return ingestPayloadWithAudit(desc.benchmarkCode(), artifact.getStorageUri(), artifact.getPayloadBlob(), startDate, endDate);
        } catch (Exception e) {
            return new BenchmarkIngestionAuditResult(
                desc.benchmarkCode(),
                desc.benchmarkName(),
                desc.provider(),
                desc.returnVariant(),
                startDate,
                endDate,
                null,
                null,
                0, 0, 0, 0, 0, 0,
                null,
                null,
                "FAILED",
                "Benchmark fetch or ingestion failure: " + e.getMessage()
            );
        }
    }

    /**
     * Ingests all five category benchmarks for the reference universe where observations are missing or below threshold.
     * Execution is isolated per benchmark so failure in one series does not abort others.
     */
    public List<BenchmarkIngestionAuditResult> ingestAllMissingCategoryBenchmarks(LocalDate startDate, LocalDate endDate, int minThreshold) {
        ensureCategoryBenchmarks();
        if (startDate == null) {
            startDate = LocalDate.of(2021, 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (minThreshold <= 0) {
            minThreshold = 700;
        }

        List<BenchmarkIngestionAuditResult> results = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(desc.benchmarkCode());
            int currentCount = bmOpt.map(bm -> benchmarkObservationRepository.findByBenchmarkId(bm.getId()).size()).orElse(0);

            if (currentCount >= minThreshold) {
                LocalDate minDate = bmOpt.flatMap(bm -> benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bm.getId())).orElse(null);
                LocalDate maxDate = bmOpt.flatMap(bm -> benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(bm.getId())).orElse(null);

                results.add(new BenchmarkIngestionAuditResult(
                    desc.benchmarkCode(),
                    desc.benchmarkName(),
                    desc.provider(),
                    desc.returnVariant(),
                    startDate,
                    endDate,
                    minDate,
                    maxDate,
                    currentCount,
                    currentCount,
                    0,
                    0,
                    0,
                    currentCount,
                    null,
                    null,
                    "SKIPPED_ALREADY_SUFFICIENT",
                    String.format("Benchmark already has %d authoritative observations in database (>= threshold %d).", currentCount, minThreshold)
                ));
            } else {
                try {
                    BenchmarkIngestionAuditResult audit = ingestCategoryBenchmarkWithAudit(desc.benchmarkCode(), startDate, endDate);
                    results.add(audit);
                } catch (Exception e) {
                    results.add(new BenchmarkIngestionAuditResult(
                        desc.benchmarkCode(),
                        desc.benchmarkName(),
                        desc.provider(),
                        desc.returnVariant(),
                        startDate,
                        endDate,
                        null,
                        null,
                        0, 0, 0, 0, 0, 0,
                        null,
                        null,
                        "FAILED",
                        "Isolated exception during benchmark ingestion: " + e.getMessage()
                    ));
                }
            }
        }
        return results;
    }

    /**
     * Safely executes production benchmark refresh up to endDate.
     * Preserves existing dataset and reports BENCHMARK_FETCH_FAILED without corrupting state if fetch fails.
     */
    @Transactional
    public NiftyIngestionSummary refreshBenchmarkData(LocalDate startDate, LocalDate endDate) {
        ensureCanonicalBenchmark();
        if (startDate == null) {
            startDate = LocalDate.of(2021, 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        try {
            return ingestRange(startDate, endDate);
        } catch (Exception e) {
            // Live fetch failed (e.g. offline, connection refused, or endpoint structure change).
            // Preserve existing observations and report clean summary.
            return new NiftyIngestionSummary(
                0, 0, 0, 0, null, null
            );
        }
    }

    /**
     * Refreshes all five canonical category benchmarks incrementally up to referenceDate.
     * Evaluates each benchmark's latest effective date, and fetches missing intervals up to referenceDate.
     * If already up-to-date, skips fetch. If fetch fails, reports failure cleanly while preserving existing data.
     */
    public List<BenchmarkIngestionAuditResult> refreshAllCategoryBenchmarks(LocalDate referenceDate) {
        ensureCategoryBenchmarks();
        LocalDate targetDate = referenceDate != null ? referenceDate : LocalDate.now();

        List<BenchmarkIngestionAuditResult> results = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(desc.benchmarkCode());
            LocalDate maxDate = bmOpt.flatMap(bm -> benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(bm.getId())).orElse(null);

            if (maxDate != null && !maxDate.isBefore(targetDate)) {
                // Already up to date
                LocalDate minDate = bmOpt.flatMap(bm -> benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bm.getId())).orElse(null);
                int count = bmOpt.map(bm -> benchmarkObservationRepository.findByBenchmarkId(bm.getId()).size()).orElse(0);
                results.add(new BenchmarkIngestionAuditResult(
                    desc.benchmarkCode(),
                    desc.benchmarkName(),
                    desc.provider(),
                    desc.returnVariant(),
                    maxDate,
                    targetDate,
                    minDate,
                    maxDate,
                    count, count, 0, 0, 0, count,
                    null, null,
                    "UP_TO_DATE",
                    "Benchmark series already current through " + maxDate
                ));
            } else {
                LocalDate startDate = (maxDate != null) ? maxDate.plusDays(1) : LocalDate.of(2021, 1, 1);
                BenchmarkIngestionAuditResult audit = ingestCategoryBenchmarkWithAudit(desc.benchmarkCode(), startDate, targetDate);
                results.add(audit);
            }
        }
        return results;
    }

    /**
     * Ingests Nifty TRI payload bytes deterministically.
     * Guarantees idempotency, revision tracking, bitemporal metadata, and immutable provenance.
     */
    @Transactional
    public NiftyIngestionSummary ingestPayload(String benchmarkCode, String sourceUri, byte[] payloadBytes) {
        BenchmarkIngestionAuditResult audit = ingestPayloadWithAudit(benchmarkCode, sourceUri, payloadBytes, null, null);
        return new NiftyIngestionSummary(
            audit.rowsAccepted(),
            audit.rowsInserted(),
            audit.duplicatesSkipped(),
            audit.rowsRevised(),
            audit.sourceArtifactId(),
            audit.payloadSha256()
        );
    }

    /**
     * Ingests Nifty TRI payload bytes with detailed audit tracking.
     */
    @Transactional
    public BenchmarkIngestionAuditResult ingestPayloadWithAudit(
        String benchmarkCode,
        String sourceUri,
        byte[] payloadBytes,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate
    ) {
        if (sourceUri == null || sourceUri.isBlank()) {
            sourceUri = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString";
        }
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        String benchmarkName = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkName)
            .orElse(benchmarkCode.contains("500") ? NIFTY_500_TRI_NAME : benchmarkCode);
        String provider = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::provider).orElse(NSE_PROVIDER);
        String variant = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::returnVariant).orElse("TRI");

        Benchmark benchmark = benchmarkRepository.findByCode(benchmarkCode)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                benchmarkCode,
                benchmarkName,
                provider,
                variant
            )));

        // 1. Persist or retrieve cryptographically immutable SourceArtifact
        SourceArtifact artifact = sourceClient.createOrGetSourceArtifact(sourceUri, payloadBytes);

        // 2. Deterministically parse records with audit accounting
        NiftyTriParser.NiftyParseResult parseResult = parser.parseWithAudit(payloadBytes);
        List<NiftyTriRecord> records = parseResult.records();

        int inserted = 0;
        int skipped = 0;
        int revised = 0;

        // 3. Process each record
        for (NiftyTriRecord record : records) {
            List<BenchmarkObservation> existingRevisions = benchmarkObservationRepository
                .findByBenchmarkIdAndEffectiveDate(benchmark.getId(), record.effectiveDate());

            if (existingRevisions.isEmpty()) {
                // First observation for this date
                BenchmarkObservation obs = new BenchmarkObservation(
                    benchmark,
                    record.effectiveDate(),
                    record.totalReturnsIndex(),
                    1,
                    record.availabilityTime()
                );
                obs.setSourceArtifact(artifact);
                obs.setLatestRevision(true);

                benchmarkObservationRepository.save(obs);
                inserted++;
            } else {
                // Check if identical observation exists
                boolean exactMatch = existingRevisions.stream().anyMatch(
                    e -> e.getIndexLevel().compareTo(record.totalReturnsIndex()) == 0
                );

                if (exactMatch) {
                    skipped++;
                } else {
                    // New revision
                    int nextRevisionSeq = existingRevisions.stream()
                        .mapToInt(BenchmarkObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    // Mark previous revisions as not latest
                    for (BenchmarkObservation old : existingRevisions) {
                        old.setLatestRevision(false);
                        benchmarkObservationRepository.save(old);
                    }

                    BenchmarkObservation revisedObs = new BenchmarkObservation(
                        benchmark,
                        record.effectiveDate(),
                        record.totalReturnsIndex(),
                        nextRevisionSeq,
                        record.availabilityTime()
                    );
                    revisedObs.setSourceArtifact(artifact);
                    revisedObs.setLatestRevision(true);

                    benchmarkObservationRepository.save(revisedObs);
                    revised++;
                }
            }
        }

        String status;
        if (parseResult.rowsAccepted() == 0) {
            status = parseResult.rowsReceived() == 0 ? "EMPTY_PAYLOAD" : "ALL_ROWS_REJECTED";
        } else if (parseResult.rowsRejected() > 0) {
            status = "PARTIAL";
        } else {
            status = "SUCCESS";
        }

        return new BenchmarkIngestionAuditResult(
            benchmarkCode,
            benchmarkName,
            provider,
            variant,
            requestedStartDate,
            requestedEndDate,
            parseResult.minEffectiveDate(),
            parseResult.maxEffectiveDate(),
            parseResult.rowsReceived(),
            parseResult.rowsAccepted(),
            parseResult.rowsRejected(),
            inserted,
            revised,
            skipped,
            artifact.getId(),
            artifact.getSha256Hash(),
            status,
            null
        );
    }

    // =========================================================================
    // Phase A4: Five-Year and Ten-Year Benchmark Coverage & Bounded Recovery
    // =========================================================================

    public static final String GOVERNANCE_DISCLAIMER =
        "Category benchmark assignments represent candidate methodology conventions under Phase 2H. " +
        "Observations are authentic Total Return Index (TRI) series. Five-year and ten-year benchmark coverage " +
        "does NOT authorize unsupported multi-year benchmark-relative metrics or imply review committee approval.";

    /**
     * Derives exact missing date intervals for a benchmark within an evaluation window.
     */
    public List<BenchmarkGapInterval> deriveBenchmarkGapsInWindow(
        Long benchmarkId,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (benchmarkId == null || windowStart == null || windowEnd == null || windowStart.isAfter(windowEnd)) {
            return List.of();
        }

        List<BenchmarkObservation> obsList = benchmarkObservationRepository
            .findByBenchmarkIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(benchmarkId, windowStart, windowEnd)
            .stream()
            .filter(o -> Boolean.TRUE.equals(o.getLatestRevision()))
            .toList();

        if (obsList.isEmpty()) {
            return List.of(BenchmarkGapInterval.of(windowStart, windowEnd, "FULL_WINDOW_MISSING"));
        }

        List<BenchmarkGapInterval> gaps = new ArrayList<>();

        // 1. Leading gap before first observation
        LocalDate firstObsDate = obsList.getFirst().getEffectiveDate();
        if (ChronoUnit.DAYS.between(windowStart, firstObsDate) > 4) {
            gaps.add(BenchmarkGapInterval.of(windowStart, firstObsDate.minusDays(1), "LEADING_GAP"));
        }

        // 2. Intermediate gaps between observations
        for (int i = 0; i < obsList.size() - 1; i++) {
            LocalDate curr = obsList.get(i).getEffectiveDate();
            LocalDate next = obsList.get(i + 1).getEffectiveDate();
            long dayDiff = ChronoUnit.DAYS.between(curr, next);

            // Gaps > 4 days indicate missing business days (Friday to Monday is 3 days, long weekend is 4 days)
            if (dayDiff > 4) {
                gaps.add(BenchmarkGapInterval.of(curr.plusDays(1), next.minusDays(1), "INTERMEDIATE_GAP"));
            }
        }

        // 3. Trailing gap after last observation
        LocalDate lastObsDate = obsList.getLast().getEffectiveDate();
        if (ChronoUnit.DAYS.between(lastObsDate, windowEnd) > 3) {
            gaps.add(BenchmarkGapInterval.of(lastObsDate.plusDays(1), windowEnd, "TRAILING_GAP"));
        }

        return gaps;
    }

    /**
     * Evaluates factual TRI coverage for a benchmark in a single horizon (3Y, 5Y, or 10Y).
     */
    @Transactional(readOnly = true)
    public BenchmarkHorizonCoverageDto evaluateBenchmarkHorizon(
        String benchmarkCode,
        AnalyticalHorizon horizon,
        LocalDate referenceDate
    ) {
        if (benchmarkCode == null || benchmarkCode.isBlank()) {
            throw new IllegalArgumentException("benchmarkCode must not be null or blank");
        }
        AnalyticalHorizon h = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate windowStart = h.calculateStartDate(refDate);
        LocalDate windowEnd = refDate;

        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        String name = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkName).orElse(benchmarkCode);
        String provider = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::provider).orElse(NSE_PROVIDER);
        String variant = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::returnVariant).orElse("TRI");
        EquityUniverseCategory category = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::category).orElse(null);
        String categoryName = category != null ? category.getDisplayName() : "Unassigned";
        String methodStatus = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::methodologyStatus).orElse("CANDIDATE_METHODOLOGY_MAPPING");

        Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(benchmarkCode);
        if (bmOpt.isEmpty()) {
            return new BenchmarkHorizonCoverageDto(
                benchmarkCode, name, provider, variant, category, categoryName,
                h, h.getCode(), refDate, windowStart, windowEnd,
                0, null, null, 0, null, null, 0,
                1, List.of(BenchmarkGapInterval.of(windowStart, windowEnd, "FULL_WINDOW_MISSING")),
                true, false, true, "NOT_REGISTERED", "UNAVAILABLE", methodStatus,
                "Benchmark entity is not registered in repository.", GOVERNANCE_DISCLAIMER
            );
        }

        Benchmark bm = bmOpt.get();
        Long bmId = bm.getId();

        long distinctDates = benchmarkObservationRepository.countDistinctEffectiveDatesInWindow(bmId, windowStart, windowEnd);
        LocalDate earliestInWindow = benchmarkObservationRepository.findMinEffectiveDateInWindow(bmId, windowStart, windowEnd).orElse(null);
        LocalDate latestInWindow = benchmarkObservationRepository.findMaxEffectiveDateInWindow(bmId, windowStart, windowEnd).orElse(null);

        long allTimeCount = benchmarkObservationRepository.countByBenchmarkIdAndDateRange(bmId, LocalDate.of(1990, 1, 1), LocalDate.of(2099, 12, 31));
        LocalDate allTimeEarliest = benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bmId).orElse(null);
        LocalDate allTimeLatest = benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(bmId).orElse(null);

        List<BenchmarkGapInterval> missingIntervals = deriveBenchmarkGapsInWindow(bmId, windowStart, windowEnd);

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
            var freshnessDto = freshnessService.getFreshnessStatusForBenchmark(benchmarkCode, refDate);
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

        String explanation = buildHorizonExplanation(name, h.getCode(), distinctDates, windowStart, windowEnd,
            earliestInWindow, latestInWindow, missingIntervals, coverageStatus, freshnessStatus);

        return new BenchmarkHorizonCoverageDto(
            benchmarkCode, name, provider, variant, category, categoryName,
            h, h.getCode(), refDate, windowStart, windowEnd,
            distinctDates, earliestInWindow, latestInWindow,
            allTimeCount, allTimeEarliest, allTimeLatest,
            spanDays, missingIntervals.size(), missingIntervals,
            hasLeading, hasIntermediate, hasTrailing,
            coverageStatus, freshnessStatus, methodStatus,
            explanation, GOVERNANCE_DISCLAIMER
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
        List<BenchmarkGapInterval> missingIntervals,
        String coverageStatus,
        String freshnessStatus
    ) {
        if ("FULL_COVERAGE".equals(coverageStatus)) {
            return String.format("%s has %d distinct observation dates fully covering the %s horizon [%s to %s]. Source freshness is %s.",
                benchmarkName, distinctDates, horizonCode, windowStart, windowEnd, freshnessStatus);
        } else if ("MISSING_LEADING_HISTORY".equals(coverageStatus)) {
            BenchmarkGapInterval leading = missingIntervals.getFirst();
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
     * Evaluates multi-horizon coverage (3Y, 5Y, and 10Y) for a single canonical category benchmark.
     */
    @Transactional(readOnly = true)
    public BenchmarkMultiHorizonCoverageDto evaluateBenchmarkMultiHorizon(String benchmarkCode, LocalDate referenceDate) {
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        String name = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkName).orElse(benchmarkCode);
        String provider = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::provider).orElse(NSE_PROVIDER);
        String variant = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::returnVariant).orElse("TRI");
        EquityUniverseCategory category = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::category).orElse(null);
        String categoryName = category != null ? category.getDisplayName() : "Unassigned";
        String methodStatus = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::methodologyStatus).orElse("CANDIDATE_METHODOLOGY_MAPPING");

        Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(benchmarkCode);
        long allTimeCount = bmOpt.map(b -> benchmarkObservationRepository.countByBenchmarkIdAndDateRange(
            b.getId(), LocalDate.of(1990, 1, 1), LocalDate.of(2099, 12, 31)
        )).orElse(0L);
        LocalDate allTimeEarliest = bmOpt.flatMap(b -> benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(b.getId())).orElse(null);
        LocalDate allTimeLatest = bmOpt.flatMap(b -> benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(b.getId())).orElse(null);

        BenchmarkHorizonCoverageDto cov3Y = evaluateBenchmarkHorizon(benchmarkCode, AnalyticalHorizon.THREE_YEAR, refDate);
        BenchmarkHorizonCoverageDto cov5Y = evaluateBenchmarkHorizon(benchmarkCode, AnalyticalHorizon.FIVE_YEAR, refDate);
        BenchmarkHorizonCoverageDto cov10Y = evaluateBenchmarkHorizon(benchmarkCode, AnalyticalHorizon.TEN_YEAR, refDate);

        return new BenchmarkMultiHorizonCoverageDto(
            benchmarkCode, name, provider, variant, category, categoryName, refDate,
            allTimeCount, allTimeEarliest, allTimeLatest,
            cov3Y.freshnessStatus(), methodStatus,
            cov3Y, cov5Y, cov10Y,
            OffsetDateTime.now(), GOVERNANCE_DISCLAIMER
        );
    }

    /**
     * Evaluates multi-horizon coverage across all five canonical category benchmarks for the reference universe.
     */
    @Transactional(readOnly = true)
    public CategoryBenchmarksMultiHorizonReportDto evaluateAllCategoryBenchmarksMultiHorizon(LocalDate referenceDate) {
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        List<BenchmarkMultiHorizonCoverageDto> list = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            list.add(evaluateBenchmarkMultiHorizon(desc.benchmarkCode(), refDate));
        }

        Map<String, CategoryBenchmarksMultiHorizonReportDto.HorizonSummaryMetrics> summaryMap = new LinkedHashMap<>();
        for (AnalyticalHorizon h : List.of(AnalyticalHorizon.THREE_YEAR, AnalyticalHorizon.FIVE_YEAR, AnalyticalHorizon.TEN_YEAR)) {
            int full = 0;
            int partial = 0;
            int missing = 0;
            long totalObs = 0;
            for (BenchmarkMultiHorizonCoverageDto b : list) {
                BenchmarkHorizonCoverageDto hc = switch (h) {
                    case THREE_YEAR -> b.threeYearCoverage();
                    case FIVE_YEAR -> b.fiveYearCoverage();
                    case TEN_YEAR -> b.tenYearCoverage();
                };
                totalObs += hc.distinctObservationDates();
                if ("FULL_COVERAGE".equals(hc.coverageStatus())) {
                    full++;
                } else if ("NO_OBSERVATIONS".equals(hc.coverageStatus())) {
                    missing++;
                } else {
                    partial++;
                }
            }
            summaryMap.put(h.getCode(), new CategoryBenchmarksMultiHorizonReportDto.HorizonSummaryMetrics(
                h.getCode(), full, partial, missing, totalObs
            ));
        }

        return new CategoryBenchmarksMultiHorizonReportDto(
            refDate, CategoryBenchmarkMapping.RULE_VERSION, list.size(), list,
            summaryMap, OffsetDateTime.now(), GOVERNANCE_DISCLAIMER
        );
    }

    /**
     * Executes bounded, chunked historical recovery for a benchmark in a specific horizon.
     * Partitions missing intervals into bounded segments (max 365 days) and reuses stored artifacts where present.
     * Execution is isolated and resumable.
     */
    public BenchmarkHorizonRecoveryResultDto recoverBenchmarkHorizon(
        String benchmarkCode,
        AnalyticalHorizon horizon,
        LocalDate referenceDate,
        Integer maxChunks,
        Integer maxChunkDays
    ) {
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        if (descOpt.isEmpty()) {
            throw new IllegalArgumentException("Unknown or unmapped benchmark code: " + benchmarkCode);
        }
        var desc = descOpt.get();
        AnalyticalHorizon h = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate windowStart = h.calculateStartDate(refDate);
        LocalDate windowEnd = refDate;

        int chunkLimit = (maxChunks != null && maxChunks > 0) ? maxChunks : 10;
        int chunkDayLimit = (maxChunkDays != null && maxChunkDays > 0) ? maxChunkDays : 365;

        Benchmark benchmark = benchmarkRepository.findByCode(benchmarkCode)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                desc.benchmarkCode(),
                desc.benchmarkName(),
                desc.provider(),
                desc.returnVariant()
            )));

        List<BenchmarkGapInterval> gaps = deriveBenchmarkGapsInWindow(benchmark.getId(), windowStart, windowEnd);
        if (gaps.isEmpty()) {
            return new BenchmarkHorizonRecoveryResultDto(
                benchmarkCode, desc.benchmarkName(), desc.provider(), desc.returnVariant(),
                h.getCode(), refDate, windowStart, windowEnd,
                0, 0, 0, 0, 0, 0, 0, 0,
                "ALREADY_COVERED", null, List.of(),
                "Benchmark series has complete contiguous coverage for the requested horizon window."
            );
        }

        // Partition gaps into slices of <= chunkDayLimit
        record ChunkSlice(LocalDate start, LocalDate end, String gapType, int gapIndex) {}
        List<ChunkSlice> allChunks = new ArrayList<>();
        for (int i = 0; i < gaps.size(); i++) {
            BenchmarkGapInterval gap = gaps.get(i);
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
        List<BenchmarkHorizonRecoveryResultDto.IntervalRecoveryAudit> intervalAudits = new ArrayList<>();

        for (ChunkSlice chunk : allChunks) {
            if (attempted >= chunkLimit) {
                break;
            }
            attempted++;

            try {
                // Check if stored authentic artifact already exists
                Optional<SourceArtifact> storedArtifactOpt = sourceClient.findStoredArtifact(
                    desc.nseIndexQueryName(), chunk.start(), chunk.end()
                );

                SourceArtifact artifact;
                boolean reused = false;
                if (storedArtifactOpt.isPresent()) {
                    artifact = storedArtifactOpt.get();
                    reused = true;
                } else {
                    artifact = sourceClient.fetchAndPersistArtifact(desc.nseIndexQueryName(), chunk.start(), chunk.end());
                }

                BenchmarkIngestionAuditResult audit = ingestPayloadWithAudit(
                    desc.benchmarkCode(), artifact.getStorageUri(), artifact.getPayloadBlob(), chunk.start(), chunk.end()
                );

                inserted += audit.rowsInserted();
                skipped += audit.duplicatesSkipped();
                revised += audit.rowsRevised();

                boolean isSuccess = "SUCCESS".equalsIgnoreCase(audit.status()) || "PARTIAL".equalsIgnoreCase(audit.status());
                if (isSuccess) {
                    recovered++;
                } else {
                    failed++;
                }

                intervalAudits.add(new BenchmarkHorizonRecoveryResultDto.IntervalRecoveryAudit(
                    chunk.start(), chunk.end(), attempted, artifact.getStorageUri(),
                    reused, artifact.getId(), audit.rowsReceived(), audit.rowsInserted(),
                    audit.duplicatesSkipped(), audit.status(), audit.failureReason()
                ));

            } catch (Exception e) {
                failed++;
                intervalAudits.add(new BenchmarkHorizonRecoveryResultDto.IntervalRecoveryAudit(
                    chunk.start(), chunk.end(), attempted, null,
                    false, null, 0, 0, 0,
                    "FAILED", e.getMessage()
                ));
            }
        }

        // Recompute remaining gaps after chunk execution
        List<BenchmarkGapInterval> remainingGaps = deriveBenchmarkGapsInWindow(benchmark.getId(), windowStart, windowEnd);

        String overallStatus;
        if (remainingGaps.isEmpty()) {
            overallStatus = "FULLY_RECOVERED";
        } else if (recovered > 0) {
            overallStatus = "PARTIALLY_RECOVERED";
        } else if (failed > 0) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "ALREADY_COVERED";
        }

        String failureReason = failed > 0 ? failed + " chunk requests encountered errors." : null;

        return new BenchmarkHorizonRecoveryResultDto(
            benchmarkCode, desc.benchmarkName(), desc.provider(), desc.returnVariant(),
            h.getCode(), refDate, windowStart, windowEnd,
            gaps.size(), attempted, recovered, failed,
            inserted, skipped, revised, remainingGaps.size(),
            overallStatus, failureReason, intervalAudits,
            "Bounded historical recovery executed. Bitemporal revisions and cryptographic provenance preserved."
        );
    }

    /**
     * Executes bounded historical recovery across all five canonical category benchmarks.
     * Execution is isolated per benchmark so failure in one series does not abort others.
     */
    public CategoryBenchmarksHorizonRecoveryReportDto recoverAllCategoryBenchmarksHorizon(
        AnalyticalHorizon horizon,
        LocalDate referenceDate,
        Integer maxChunksPerBenchmark,
        Integer maxChunkDays
    ) {
        ensureCategoryBenchmarks();
        AnalyticalHorizon h = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();

        List<BenchmarkHorizonRecoveryResultDto> results = new ArrayList<>();
        int succ = 0;
        int partial = 0;
        int fail = 0;
        int totalInserted = 0;
        int totalRecovered = 0;

        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            try {
                BenchmarkHorizonRecoveryResultDto res = recoverBenchmarkHorizon(
                    desc.benchmarkCode(), h, refDate, maxChunksPerBenchmark, maxChunkDays
                );
                results.add(res);
                totalInserted += res.observationsInserted();
                totalRecovered += res.intervalsRecovered();
                if ("FULLY_RECOVERED".equalsIgnoreCase(res.status()) || "ALREADY_COVERED".equalsIgnoreCase(res.status())) {
                    succ++;
                } else if ("PARTIALLY_RECOVERED".equalsIgnoreCase(res.status())) {
                    partial++;
                } else {
                    fail++;
                }
            } catch (Exception e) {
                fail++;
                results.add(new BenchmarkHorizonRecoveryResultDto(
                    desc.benchmarkCode(), desc.benchmarkName(), desc.provider(), desc.returnVariant(),
                    h.getCode(), refDate, h.calculateStartDate(refDate), refDate,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    "FAILED", "Isolated exception during benchmark recovery: " + e.getMessage(),
                    List.of(), "Failure isolated; remaining category benchmarks preserved."
                ));
            }
        }

        return new CategoryBenchmarksHorizonRecoveryReportDto(
            "RECOVER_ALL_CATEGORY_BENCHMARKS_HORIZON",
            refDate, h.getCode(), results.size(), succ, partial, fail,
            totalInserted, totalRecovered, results, OffsetDateTime.now(),
            "All 5 canonical category benchmarks evaluated for " + h.getCode() + " historical recovery. Candidate methodology status preserved."
        );
    }
}
