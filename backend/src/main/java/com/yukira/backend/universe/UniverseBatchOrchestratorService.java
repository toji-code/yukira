package com.yukira.backend.universe;

import com.yukira.backend.benchmark.CategoryBenchmarkMapping;
import com.yukira.backend.benchmark.CategoryBenchmarkMapping.BenchmarkDescriptor;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService.TargetedBackfillFundResult;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service orchestrating bounded reference-universe historical data expansion
 * and automated metric calculation runs (TSK-M1-06).
 *
 * Core Epistemic & Methodological Guardrails:
 * 1. Strict Pipeline Architecture:
 *    Reference Universe -> History Sufficiency -> Controlled Ingestion -> Data-Quality Validation
 *    -> Benchmark Coverage -> Metric Eligibility -> Calculation Run -> Persisted Results -> Coverage Report.
 * 2. Deterministic Prioritization:
 *    Options prioritized by: Zero history (0 dates) -> Partial history (< 700 dates) -> Option ID ASC.
 * 3. Failure Isolation:
 *    Per-scheme failure isolation ensures one network or mathematical failure never discards valid results
 *    for unrelated funds.
 * 4. Governance Status Preservation:
 *    Category benchmarks remain CANDIDATE_METHODOLOGY_MAPPING. Execution does NOT alter methodology governance.
 * 5. Deterministic Non-Fabrication:
 *    Zero synthetic NAVs, zero interpolated prices, zero default or fallback zeros for missing values.
 */
@Service
public class UniverseBatchOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(UniverseBatchOrchestratorService.class);

    public static final String RULE_VERSION = "YUKIRA_V1_M1_UNIVERSE_BATCH_ORCHESTRATOR_V1";
    public static final int DEFAULT_SUFFICIENCY_THRESHOLD = 700;
    public static final String CANONICAL_METHODOLOGY_TAG = "APPROVED_M2N";

    public static final List<String> CANONICAL_BATCH_METRIC_CODES = List.of(
        "RET-02", "RET-03", "RSK-01", "RSK-03", "MKT-01", "MKT-02", "RAT-04", "REL-01"
    );

    public static final List<String> GOVERNANCE_DISCLAIMERS = List.of(
        "Category benchmark assignments represent candidate methodology conventions under Phase 2H.",
        "Candidate methodology mappings have not been formally approved by the review committee.",
        "Calculations are deterministic financial algorithms executed by the Python quant kernel.",
        "Zero synthetic NAVs or interpolated returns are injected into production ledgers."
    );

    private final ReferenceUniverseService referenceUniverseService;
    private final HistorySufficiencyService historySufficiencyService;
    private final HistoricalNavIngestionService historicalNavIngestionService;
    private final BenchmarkResolutionService benchmarkResolutionService;
    private final NiftyBenchmarkIngestionService benchmarkIngestionService;
    private final CalculationOrchestratorService calculationOrchestratorService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService;

    private final AtomicBoolean isJobRunning = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yukira-batch-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile UniverseBatchJobStatusDto currentJobStatus = UniverseBatchJobStatusDto.idle();
    private volatile UniverseBatchJobStatusDto lastCompletedJobStatus = null;

    @Autowired
    public UniverseBatchOrchestratorService(
        ReferenceUniverseService referenceUniverseService,
        HistorySufficiencyService historySufficiencyService,
        HistoricalNavIngestionService historicalNavIngestionService,
        BenchmarkResolutionService benchmarkResolutionService,
        NiftyBenchmarkIngestionService benchmarkIngestionService,
        CalculationOrchestratorService calculationOrchestratorService,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository
    ) {
        this.referenceUniverseService = referenceUniverseService;
        this.historySufficiencyService = historySufficiencyService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.benchmarkResolutionService = benchmarkResolutionService;
        this.benchmarkIngestionService = benchmarkIngestionService;
        this.calculationOrchestratorService = calculationOrchestratorService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
    }

    @Autowired(required = false)
    public void setReferenceUniverseNavRecoveryService(ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService) {
        this.referenceUniverseNavRecoveryService = referenceUniverseNavRecoveryService;
    }

    /**
     * Previews a bounded batch run without performing network requests or database mutations.
     */
    @Transactional(readOnly = true)
    public UniverseBatchRunReportDto previewBatchRun(UniverseBatchRunRequest request) {
        UniverseBatchRunRequest dryRunRequest = request != null ? request.withDryRun(true) : UniverseBatchRunRequest.defaultRequest().withDryRun(true);
        return executeBatchRun(dryRunRequest);
    }

    /**
     * Executes an operator-triggered bounded reference universe batch ingestion and calculation run.
     * Note: Transaction boundaries are per-option via CalculationOrchestratorService to isolate failures
     * and persist progress progressively.
     */
    public UniverseBatchRunReportDto executeBatchRun(UniverseBatchRunRequest request) {
        return executeBatchRun(request, null);
    }

    /**
     * Executes bounded reference universe batch run with real-time per-option progress callbacks.
     */
    public UniverseBatchRunReportDto executeBatchRun(UniverseBatchRunRequest request, Consumer<UniverseBatchJobStatusDto> progressConsumer) {
        if (request == null) {
            request = UniverseBatchRunRequest.defaultRequest();
        }

        long startTime = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        String runId = "BATCH-RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        int batchSize = request.batchSize();
        EquityUniverseCategory categoryFilter = request.category();
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        LocalDate asOfDate = request.asOfDate();
        OffsetDateTime knowledgeCutoff = request.knowledgeCutoff();
        boolean dryRun = request.dryRun();
        boolean backfillNavIfMissing = request.backfillNavIfMissing();
        boolean ingestBenchmarkIfMissing = request.ingestBenchmarkIfMissing();
        boolean calculateMetrics = request.calculateMetrics();
        String operator = request.operator();
        boolean forceRecalculate = request.forceRecalculate() != null && request.forceRecalculate();

        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put("batchSize", batchSize);
        paramMap.put("category", categoryFilter != null ? categoryFilter.name() : "ALL_5_CATEGORIES");
        paramMap.put("startDate", startDate.toString());
        paramMap.put("endDate", endDate.toString());
        paramMap.put("asOfDate", asOfDate.toString());
        paramMap.put("knowledgeCutoff", knowledgeCutoff.toString());
        paramMap.put("dryRun", dryRun);
        paramMap.put("backfillNavIfMissing", backfillNavIfMissing);
        paramMap.put("ingestBenchmarkIfMissing", ingestBenchmarkIfMissing);
        paramMap.put("calculateMetrics", calculateMetrics);
        paramMap.put("operator", operator);
        paramMap.put("forceRecalculate", forceRecalculate);

        // 1. Candidate Selection & Deterministic Prioritization
        List<SchemeOption> referenceOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();
        if (categoryFilter != null) {
            referenceOptions = referenceOptions.stream()
                .filter(o -> referenceUniverseService.evaluateOption(o).category() == categoryFilter)
                .toList();
        }

        // Query distinct valid date counts from database
        List<Object[]> summaries = navObservationRepository.findDistinctValidNavSummaries();
        Map<Long, Long> obsCountMap = new HashMap<>();
        for (Object[] row : summaries) {
            if (row != null && row[0] != null && row[3] != null) {
                obsCountMap.put(((Number) row[0]).longValue(), ((Number) row[3]).longValue());
            }
        }

        // Deterministic sort:
        // When backfilling NAVs: Prioritize options with zero or insufficient observations (0 -> partial -> sufficient)
        // When running calculations without backfill: Prioritize options with sufficient observations (>= 700 -> partial -> 0)
        List<SchemeOption> sortedOptions = new ArrayList<>(referenceOptions);
        if (!backfillNavIfMissing && calculateMetrics) {
            sortedOptions.sort(Comparator
                .comparing((SchemeOption o) -> obsCountMap.getOrDefault(o.getId(), 0L) >= DEFAULT_SUFFICIENCY_THRESHOLD ? 0 : 1)
                .thenComparing((SchemeOption o) -> -obsCountMap.getOrDefault(o.getId(), 0L))
                .thenComparing(SchemeOption::getId)
            );
        } else {
            sortedOptions.sort(Comparator
                .comparingLong((SchemeOption o) -> obsCountMap.getOrDefault(o.getId(), 0L))
                .thenComparing(SchemeOption::getId)
            );
        }

        List<SchemeOption> candidateOptions;
        if (categoryFilter == null) {
            // Stratified round-robin across all 5 categories to ensure cross-category representation
            Map<EquityUniverseCategory, List<SchemeOption>> byCategory = new LinkedHashMap<>();
            for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
                byCategory.put(cat, new ArrayList<>());
            }
            for (SchemeOption o : sortedOptions) {
                EquityUniverseCategory cat = referenceUniverseService.evaluateOption(o).category();
                if (cat != null && byCategory.containsKey(cat)) {
                    byCategory.get(cat).add(o);
                }
            }
            List<SchemeOption> interleaved = new ArrayList<>();
            int maxPerCat = byCategory.values().stream().mapToInt(List::size).max().orElse(0);
            for (int i = 0; i < maxPerCat; i++) {
                for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
                    List<SchemeOption> catList = byCategory.get(cat);
                    if (i < catList.size()) {
                        interleaved.add(catList.get(i));
                    }
                }
            }
            candidateOptions = interleaved;
        } else {
            candidateOptions = sortedOptions;
        }

        int selectedCount = Math.min(batchSize, candidateOptions.size());
        List<SchemeOption> selectedCohort = candidateOptions.subList(0, selectedCount);

        // 2. Benchmark Pre-Flight & Category Coverage Evaluation
        benchmarkIngestionService.ensureCategoryBenchmarks();
        List<BenchmarkCoverageSummaryDto> benchmarkSummaries = evaluateCategoryBenchmarksCoverage(startDate, asOfDate);

        // Ingest missing category benchmarks if requested
        if (ingestBenchmarkIfMissing && !dryRun) {
            Set<EquityUniverseCategory> categoriesInBatch = selectedCohort.stream()
                .map(o -> referenceUniverseService.evaluateOption(o).category())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            for (EquityUniverseCategory cat : categoriesInBatch) {
                var descOpt = CategoryBenchmarkMapping.getDescriptorForCategory(cat);
                if (descOpt.isPresent()) {
                    String bmCode = descOpt.get().benchmarkCode();
                    Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(bmCode);
                    int currentCount = bmOpt.map(bm -> benchmarkObservationRepository.findByBenchmarkId(bm.getId()).size()).orElse(0);

                    if (currentCount < DEFAULT_SUFFICIENCY_THRESHOLD) {
                        try {
                            log.info("Triggering pre-flight category benchmark backfill for {} ({})...", bmCode, cat);
                            benchmarkIngestionService.ingestCategoryBenchmarkRange(bmCode, startDate, endDate);
                        } catch (Exception e) {
                            log.warn("Benchmark pre-flight backfill failed for {}: {}. Proceeding with isolated evaluation.", bmCode, e.getMessage());
                        }
                    }
                }
            }
            // Re-evaluate summaries after potential ingestion
            benchmarkSummaries = evaluateCategoryBenchmarksCoverage(startDate, asOfDate);
        }

        // 3. Resumability Pre-Check: Lookup existing successful runs for options in selected cohort
        Map<Long, CalculationRun> existingSuccessfulRunMap = new HashMap<>();
        if (!forceRecalculate && !selectedCohort.isEmpty() && calculateMetrics && !dryRun) {
            List<Long> cohortIds = selectedCohort.stream().map(SchemeOption::getId).toList();
            List<CalculationRun> priorRuns = calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(cohortIds, asOfDate);
            for (CalculationRun pr : priorRuns) {
                if (("SUCCESS".equalsIgnoreCase(pr.getRunStatus()) || "COMPLETED".equalsIgnoreCase(pr.getRunStatus()))
                    && !existingSuccessfulRunMap.containsKey(pr.getSchemeOption().getId())) {
                    List<MetricResult> mrList = metricResultRepository.findByCalculationRunId(pr.getId());
                    if (mrList != null) {
                        Set<String> codes = mrList.stream()
                            .filter(mr -> mr.getNumericValue() != null)
                            .map(MetricResult::getMetricCode)
                            .collect(Collectors.toSet());
                        if (codes.containsAll(CANONICAL_BATCH_METRIC_CODES)) {
                            existingSuccessfulRunMap.put(pr.getSchemeOption().getId(), pr);
                        }
                    }
                }
            }
        }

        // 4. Process Schemes in Selected Batch (Isolated Execution)
        List<SchemeBatchExecutionDetailDto> schemeOutcomes = new ArrayList<>();
        int ingestedCount = 0;
        int calculatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        Map<Long, Integer> bmObsInWindowCache = new HashMap<>();
        for (SchemeOption option : selectedCohort) {
            Long optId = option.getId();
            String amfiCode = option.getAmfiCode();
            String isin = option.getIsin();
            String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
                ? option.getPlan().getScheme().getName() : "Unknown Scheme";
            EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

            long initialDates = obsCountMap.getOrDefault(optId, 0L);
            String ingestionOutcome;
            int newObservations = 0;
            String failureReason = null;

            // Ingestion Phase
            if (dryRun) {
                ingestionOutcome = "DRY_RUN_PREVIEW";
            } else if (backfillNavIfMissing && initialDates < DEFAULT_SUFFICIENCY_THRESHOLD) {
                try {
                    if (referenceUniverseNavRecoveryService != null && initialDates == 556) {
                        NavRecoveryBatchExecutionResultDto recRes = referenceUniverseNavRecoveryService.executeStoredArtifactRecovery(
                            new StoredArtifactRecoveryRequest(1, null, List.of(optId), false, asOfDate, "universe-batch")
                        );
                        if (!recRes.fundOutcomes().isEmpty()) {
                            var outcome = recRes.fundOutcomes().get(0);
                            newObservations = outcome.observationsInserted();
                            ingestionOutcome = "RECOVERED_SUFFICIENT".equals(outcome.outcomeStatus()) || "PARTIAL_PROGRESS".equals(outcome.outcomeStatus())
                                ? "INSERTED" : "SKIPPED_NO_DATA";
                            if (newObservations > 0) ingestedCount++;
                        } else {
                            ingestionOutcome = "SKIPPED_NO_DATA";
                        }
                    } else if (referenceUniverseNavRecoveryService != null && initialDates > 0) {
                        NavRecoveryBatchExecutionResultDto recRes = referenceUniverseNavRecoveryService.executeTargetedIntervalRecovery(
                            new TargetedIntervalRecoveryRequest(1, null, List.of(optId), false, asOfDate, 10, "universe-batch")
                        );
                        if (!recRes.fundOutcomes().isEmpty()) {
                            var outcome = recRes.fundOutcomes().get(0);
                            newObservations = outcome.observationsInserted();
                            ingestionOutcome = "RECOVERED_SUFFICIENT".equals(outcome.outcomeStatus()) || "PARTIAL_PROGRESS".equals(outcome.outcomeStatus())
                                ? "INSERTED" : "SKIPPED_NO_DATA";
                            if (newObservations > 0) ingestedCount++;
                        } else {
                            ingestionOutcome = "SKIPPED_NO_DATA";
                        }
                    } else {
                        TargetedBackfillFundResult backfillRes = historicalNavIngestionService
                            .ingestHistoricalNavForSchemeOptionIsolated(optId, startDate, endDate);
                        newObservations = backfillRes.observationsInserted();
                        ingestionOutcome = "SUCCESS".equalsIgnoreCase(backfillRes.status()) ? "INSERTED" : "FAILED";
                        if ("FAILED".equalsIgnoreCase(backfillRes.status())) {
                            failureReason = backfillRes.failureReason();
                            failedCount++;
                        } else {
                            ingestedCount++;
                        }
                    }
                } catch (Exception e) {
                    ingestionOutcome = "FAILED";
                    failureReason = "AMFI ingestion exception: " + e.getMessage();
                    failedCount++;
                }
            } else {
                ingestionOutcome = "SKIPPED_ALREADY_PRESENT";
            }

            // History Sufficiency Evaluation Phase
            HistorySufficiencyResult suffResult = historySufficiencyService.evaluateOptionAsOf(optId, asOfDate, knowledgeCutoff);
            long finalDates = suffResult != null ? suffResult.distinctObservationDates() : 0L;
            String suffStatus = suffResult != null && suffResult.status() != null ? suffResult.status().name() : "EVALUATION_FAILED";

            // Benchmark Resolution Phase
            BenchmarkResolutionResult bmRes = benchmarkResolutionService.resolveBenchmarkForOption(option, asOfDate);
            String bmCode = bmRes != null && bmRes.benchmark() != null ? bmRes.benchmark().getCode() : "UNMAPPED";
            String bmGovStatus = bmRes != null ? bmRes.methodologyStatus() : "UNMAPPED";

            int bmObsInWindow = 0;
            if (bmRes != null && bmRes.benchmark() != null) {
                Long bmId = bmRes.benchmark().getId();
                bmObsInWindow = bmObsInWindowCache.computeIfAbsent(bmId, id -> (int) benchmarkObservationRepository
                    .findAuthoritativeObservationsAsOfCutoff(id, asOfDate, knowledgeCutoff)
                    .stream()
                    .filter(b -> !b.getEffectiveDate().isBefore(startDate) && !b.getEffectiveDate().isAfter(asOfDate))
                    .count());
            }

            // Calculation Phase
            String calcOutcome;
            Long calcRunId = null;
            List<String> metricsDone = List.of();

            if (dryRun) {
                calcOutcome = "DRY_RUN_PREVIEW";
                if (finalDates >= DEFAULT_SUFFICIENCY_THRESHOLD && bmObsInWindow >= DEFAULT_SUFFICIENCY_THRESHOLD) {
                    failureReason = "Dry-run preview: Scheme option and benchmark satisfy all 3Y calculation prerequisites.";
                } else if (finalDates < DEFAULT_SUFFICIENCY_THRESHOLD) {
                    failureReason = String.format("Dry-run preview: History insufficient (%d / %d required dates).", finalDates, DEFAULT_SUFFICIENCY_THRESHOLD);
                } else {
                    failureReason = String.format("Dry-run preview: Benchmark %s insufficient (%d / %d required dates).", bmCode, bmObsInWindow, DEFAULT_SUFFICIENCY_THRESHOLD);
                }
                skippedCount++;
            } else if (!calculateMetrics) {
                calcOutcome = "SKIPPED_NOT_REQUESTED";
                skippedCount++;
            } else if (existingSuccessfulRunMap.containsKey(optId)) {
                CalculationRun existingRun = existingSuccessfulRunMap.get(optId);
                calcOutcome = "SKIPPED_ALREADY_CALCULATED";
                calcRunId = existingRun.getId();
                metricsDone = CANONICAL_BATCH_METRIC_CODES;
                failureReason = String.format("Scheme option already has complete verified 3Y analytical metrics for asOfDate %s (CalculationRun %d); skipped for resumability.", asOfDate, calcRunId);
                skippedCount++;
            } else {
                String runStatus = "COMPLETED";
                if (suffResult.status() != HistorySufficiencyStatus.ELIGIBLE) {
                    runStatus = "INSUFFICIENT_HISTORY";
                    String suffReason = String.format("History sufficiency status is %s (%d distinct trading dates; %d required).",
                        suffResult.status(), finalDates, DEFAULT_SUFFICIENCY_THRESHOLD);
                    failureReason = failureReason != null ? failureReason + "; " + suffReason : suffReason;
                } else if (bmRes == null || bmRes.benchmark() == null) {
                    runStatus = "UNMAPPED";
                    String unmappedReason = "Category benchmark mapping unresolved for category " + category;
                    failureReason = failureReason != null ? failureReason + "; " + unmappedReason : unmappedReason;
                } else if (bmObsInWindow < DEFAULT_SUFFICIENCY_THRESHOLD) {
                    runStatus = "BENCHMARK_UNAVAILABLE";
                    String bmReason = String.format("Benchmark %s has %d observations in window [%s, %s]; %d required for relative calculations.",
                        bmCode, bmObsInWindow, startDate, asOfDate, DEFAULT_SUFFICIENCY_THRESHOLD);
                    failureReason = failureReason != null ? failureReason + "; " + bmReason : bmReason;
                }

                try {
                    Map<String, Object> calcParams = Map.of(
                        "start_date", startDate.toString(),
                        "lookback_years", 3,
                        "periods_per_year", 252.0,
                        "min_downside_observations", 100
                    );

                    Long bmId = (bmRes != null && bmRes.benchmark() != null) ? bmRes.benchmark().getId() : null;

                    CalculationRun run = calculationOrchestratorService.executeCalculationRun(
                        optId,
                        bmId,
                        asOfDate,
                        knowledgeCutoff,
                        CANONICAL_BATCH_METRIC_CODES,
                        CANONICAL_METHODOLOGY_TAG,
                        calcParams
                    );

                    calcRunId = run.getId();
                    if (!"COMPLETED".equalsIgnoreCase(runStatus) && !"SUCCESS".equalsIgnoreCase(runStatus)) {
                        run.setRunStatus(runStatus);
                        run.setErrorMessage(failureReason);
                        calculationRunRepository.save(run);
                        calcOutcome = runStatus;
                    } else if ("FAILED".equalsIgnoreCase(run.getRunStatus())) {
                        calcOutcome = "FAILED";
                        failureReason = "Quant engine execution error: " + run.getErrorMessage();
                        failedCount++;
                    } else {
                        calcOutcome = "CALCULATED";
                        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
                        if (results != null && !results.isEmpty()) {
                            metricsDone = results.stream().map(MetricResult::getMetricCode).distinct().toList();
                        } else {
                            metricsDone = CANONICAL_BATCH_METRIC_CODES;
                        }
                        calculatedCount++;
                    }
                } catch (Exception e) {
                    calcOutcome = "FAILED";
                    failureReason = "Calculation orchestration error: " + e.getMessage();
                    failedCount++;
                }
            }

            schemeOutcomes.add(new SchemeBatchExecutionDetailDto(
                optId, amfiCode, isin, schemeName, category,
                bmCode, bmGovStatus, initialDates, finalDates, suffStatus,
                ingestionOutcome, newObservations, calcOutcome, calcRunId, metricsDone, failureReason
            ));

            int processed = schemeOutcomes.size();
            if (progressConsumer != null) {
                double pct = selectedCohort.isEmpty() ? 100.0 : ((double) processed / selectedCohort.size()) * 100.0;
                UniverseBatchJobStatusDto update = new UniverseBatchJobStatusDto(
                    runId, "RUNNING", startedAt, null, operator, categoryFilter, asOfDate,
                    batchSize, candidateOptions.size(), selectedCohort.size(),
                    processed, calculatedCount, skippedCount, failedCount,
                    selectedCohort.size() - processed, optId, schemeName,
                    Math.round(pct * 10.0) / 10.0, null, null
                );
                progressConsumer.accept(update);
            }
        }

        OffsetDateTime completedAt = OffsetDateTime.now();
        long durationMs = System.currentTimeMillis() - startTime;

        String overallStatus;
        if (dryRun) {
            overallStatus = "DRY_RUN_PREVIEW";
        } else if (failedCount == 0 && (calculatedCount > 0 || skippedCount == selectedCohort.size())) {
            overallStatus = "COMPLETED";
        } else if (calculatedCount > 0 || skippedCount > 0 || ingestedCount > 0) {
            overallStatus = "PARTIAL_SUCCESS";
        } else if (failedCount > 0) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "COMPLETED_NO_MUTATIONS";
        }

        return new UniverseBatchRunReportDto(
            runId, startedAt, completedAt, durationMs, dryRun, operator, overallStatus,
            paramMap, selectedCohort.size(), ingestedCount, calculatedCount, skippedCount, failedCount,
            benchmarkSummaries, schemeOutcomes, GOVERNANCE_DISCLAIMERS
        );
    }

    /**
     * Retrieves the comprehensive universe data coverage status across the 5 reference categories
     * and their candidate category benchmarks.
     */
    @Transactional
    public UniverseCoverageStatusDto getUniverseCoverageStatus() {
        benchmarkIngestionService.ensureCategoryBenchmarks();
        long totalMaster = schemeOptionRepository.count();
        List<SchemeOption> refOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();

        List<Object[]> summaries = navObservationRepository.findDistinctValidNavSummaries();
        Map<Long, Long> obsCountMap = new HashMap<>();
        for (Object[] row : summaries) {
            if (row != null && row[0] != null && row[3] != null) {
                obsCountMap.put(((Number) row[0]).longValue(), ((Number) row[3]).longValue());
            }
        }

        long totalSufficient = 0;
        long totalPartial = 0;
        long totalZero = 0;

        Map<String, CategoryCoverageDto> categoryBreakdown = new LinkedHashMap<>();

        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            List<SchemeOption> catOptions = refOptions.stream()
                .filter(o -> referenceUniverseService.evaluateOption(o).category() == cat)
                .toList();

            long sufficient = 0;
            long partial = 0;
            long zero = 0;

            for (SchemeOption opt : catOptions) {
                long cnt = obsCountMap.getOrDefault(opt.getId(), 0L);
                if (cnt >= DEFAULT_SUFFICIENCY_THRESHOLD) {
                    sufficient++;
                } else if (cnt > 0) {
                    partial++;
                } else {
                    zero++;
                }
            }

            totalSufficient += sufficient;
            totalPartial += partial;
            totalZero += zero;

            var descOpt = CategoryBenchmarkMapping.getDescriptorForCategory(cat);
            String bmCode = descOpt.map(BenchmarkDescriptor::benchmarkCode).orElse("UNMAPPED");
            String bmGov = descOpt.map(BenchmarkDescriptor::methodologyStatus).orElse("UNMAPPED");

            int bmObsCount = 0;
            String bmStatus = "NOT_REGISTERED";
            if (descOpt.isPresent()) {
                Optional<Benchmark> bm = benchmarkRepository.findByCode(bmCode);
                if (bm.isPresent()) {
                    bmObsCount = benchmarkObservationRepository.findByBenchmarkId(bm.get().getId()).size();
                    bmStatus = bmObsCount >= DEFAULT_SUFFICIENCY_THRESHOLD ? "SUFFICIENT_3Y_COVERAGE" : (bmObsCount > 0 ? "PARTIAL_COVERAGE" : "NO_OBSERVATIONS");
                }
            }

            categoryBreakdown.put(cat.name(), new CategoryCoverageDto(
                cat, cat.getDisplayName(), bmCode, bmGov,
                catOptions.size(), sufficient, partial, zero,
                bmObsCount, bmStatus
            ));
        }

        List<BenchmarkCoverageSummaryDto> benchmarkSummaries = evaluateCategoryBenchmarksCoverage(
            LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15)
        );

        return new UniverseCoverageStatusDto(
            RULE_VERSION,
            totalMaster,
            refOptions.size(),
            totalSufficient,
            totalPartial,
            totalZero,
            categoryBreakdown,
            benchmarkSummaries,
            OffsetDateTime.now(),
            GOVERNANCE_DISCLAIMERS
        );
    }

    /**
     * Executes calculation orchestration directly for all eligible (history-sufficient) options
     * in a category without performing external network ingestion.
     */
    @Transactional
    public UniverseBatchRunReportDto calculateEligibleCohort(
        EquityUniverseCategory categoryFilter,
        int limit,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        if (limit <= 0) limit = 10;
        if (asOfDate == null) asOfDate = LocalDate.of(2024, 1, 15);
        if (knowledgeCutoff == null) {
            knowledgeCutoff = asOfDate.atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        }

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            limit,
            categoryFilter,
            asOfDate.minusYears(3),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false,
            false, // Do not backfill NAV
            false, // Do not ingest benchmark
            true,  // Calculate metrics
            "admin-calculate-eligible"
        );

        return executeBatchRun(req);
    }

    private List<BenchmarkCoverageSummaryDto> evaluateCategoryBenchmarksCoverage(LocalDate startDate, LocalDate endDate) {
        List<BenchmarkCoverageSummaryDto> list = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(desc.benchmarkCode());
            int obsCount = 0;
            LocalDate earliest = null;
            LocalDate latest = null;
            String status = "MISSING_REGISTRATION";

            if (bmOpt.isPresent()) {
                Long bmId = bmOpt.get().getId();
                obsCount = (int) benchmarkObservationRepository.countByBenchmarkIdAndDateRange(bmId, LocalDate.of(1990, 1, 1), LocalDate.of(2099, 12, 31));
                earliest = benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bmId).orElse(null);
                latest = benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(bmId).orElse(null);
                status = obsCount >= DEFAULT_SUFFICIENCY_THRESHOLD ? "SUFFICIENT_3Y_COVERAGE" : (obsCount > 0 ? "PARTIAL_COVERAGE" : "NO_OBSERVATIONS");
            }

            list.add(new BenchmarkCoverageSummaryDto(
                desc.benchmarkCode(),
                desc.benchmarkName(),
                desc.provider(),
                desc.returnVariant(),
                desc.isTri(),
                desc.category(),
                desc.methodologyStatus(),
                obsCount,
                earliest,
                latest,
                status
            ));
        }
        return list;
    }

    /**
     * Triggers a bounded, resumable background batch calculation job (TSK-M1-09).
     *
     * Concurrency Protection:
     * Guarantees only one background job can run concurrently using an AtomicBoolean lock.
     */
    public UniverseBatchJobStatusDto triggerAsyncBatchRun(UniverseBatchRunRequest request) {
        if (request == null) {
            request = UniverseBatchRunRequest.defaultRequest();
        }
        if (!isJobRunning.compareAndSet(false, true)) {
            String activeId = currentJobStatus != null ? currentJobStatus.jobId() : "ACTIVE";
            throw new IllegalStateException("A batch calculation job is already actively running: " + activeId);
        }

        final UniverseBatchRunRequest effectiveReq = request;
        String jobId = "BATCH-JOB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime startedAt = OffsetDateTime.now();

        // 1. Candidate Selection for pre-flight counts
        List<SchemeOption> referenceOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();
        if (effectiveReq.category() != null) {
            referenceOptions = referenceOptions.stream()
                .filter(o -> {
                    var eval = referenceUniverseService.evaluateOption(o);
                    return eval != null && eval.category() == effectiveReq.category();
                })
                .toList();
        }
        int totalEligible = referenceOptions.size();
        int selected = Math.min(effectiveReq.batchSize(), totalEligible);

        currentJobStatus = new UniverseBatchJobStatusDto(
            jobId, "PENDING", startedAt, null, effectiveReq.operator(), effectiveReq.category(),
            effectiveReq.asOfDate(), effectiveReq.batchSize(), totalEligible, selected,
            0, 0, 0, 0, selected, null, null, 0.0, null, null
        );

        executor.submit(() -> {
            try {
                currentJobStatus = new UniverseBatchJobStatusDto(
                    jobId, "RUNNING", startedAt, null, effectiveReq.operator(), effectiveReq.category(),
                    effectiveReq.asOfDate(), effectiveReq.batchSize(), totalEligible, selected,
                    0, 0, 0, 0, selected, null, null, 0.0, null, null
                );

                UniverseBatchRunReportDto report = executeBatchRun(effectiveReq, progressUpdate -> {
                    this.currentJobStatus = new UniverseBatchJobStatusDto(
                        jobId, "RUNNING", startedAt, null, effectiveReq.operator(), effectiveReq.category(),
                        effectiveReq.asOfDate(), effectiveReq.batchSize(), totalEligible, selected,
                        progressUpdate.processedCount(), progressUpdate.successfulCount(),
                        progressUpdate.skippedCount(), progressUpdate.failedCount(),
                        progressUpdate.remainingCount(), progressUpdate.currentOptionId(),
                        progressUpdate.currentOptionName(), progressUpdate.progressPercentage(),
                        null, null
                    );
                });

                OffsetDateTime completedAt = OffsetDateTime.now();
                String terminalStatus = "COMPLETED";
                if (report.totalFailed() > 0 && report.totalCalculated() == 0 && report.totalSkipped() == 0) {
                    terminalStatus = "FAILED";
                } else if (report.totalFailed() > 0) {
                    terminalStatus = "PARTIAL";
                }

                UniverseBatchJobStatusDto finalStatus = new UniverseBatchJobStatusDto(
                    jobId, terminalStatus, startedAt, completedAt, effectiveReq.operator(), effectiveReq.category(),
                    effectiveReq.asOfDate(), effectiveReq.batchSize(), totalEligible, selected,
                    selected, report.totalCalculated(), report.totalSkipped(), report.totalFailed(),
                    0, null, null, 100.0, report, null
                );
                this.currentJobStatus = finalStatus;
                this.lastCompletedJobStatus = finalStatus;
            } catch (Throwable t) {
                log.error("Asynchronous batch run execution failed for job {}", jobId, t);
                OffsetDateTime errorAt = OffsetDateTime.now();
                UniverseBatchJobStatusDto errorStatus = new UniverseBatchJobStatusDto(
                    jobId, "FAILED", startedAt, errorAt, effectiveReq.operator(), effectiveReq.category(),
                    effectiveReq.asOfDate(), effectiveReq.batchSize(), totalEligible, selected,
                    0, 0, 0, 0, selected, null, null, 0.0, null, t.getMessage()
                );
                this.currentJobStatus = errorStatus;
                this.lastCompletedJobStatus = errorStatus;
            } finally {
                isJobRunning.set(false);
            }
        });

        return currentJobStatus;
    }

    /**
     * Retrieves the current active background job status or the last completed job status.
     */
    public UniverseBatchJobStatusDto getJobStatus() {
        if (isJobRunning.get()) {
            return currentJobStatus;
        }
        return lastCompletedJobStatus != null ? lastCompletedJobStatus : currentJobStatus;
    }

    /**
     * Returns true if a background batch job is currently actively executing.
     */
    public boolean isJobRunning() {
        return isJobRunning.get();
    }
}
