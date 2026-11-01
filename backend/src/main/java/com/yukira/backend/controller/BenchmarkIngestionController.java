package com.yukira.backend.controller;

import com.yukira.backend.benchmark.*;
import com.yukira.backend.dto.ingestion.BenchmarkDataFreshnessDto;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.NiftyIngestionSummary;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for NIFTY 500 TRI and category benchmark historical data ingestion and production refresh.
 *
 * Part E / F of the bounded peer cohort production batch:
 * Ingests the authoritative NIFTY 500 TRI historical series required for
 * benchmark-relative MetricResults (Alpha, Beta, Tracking Error).
 *
 * Epistemic constraints:
 *  - All observations are sourced from the official NSE Indices endpoint.
 *  - Data is persisted with cryptographic SHA-256 provenance via SourceArtifact.
 *  - Bitemporal immutability is enforced: duplicates are skipped, revisions tracked.
 *  - No look-ahead bias: ingestion window is explicitly bounded by the caller.
 *  - Quantitative calculations (quant engine & score calculation) are NOT invoked during benchmark refresh.
 */
@RestController
@RequestMapping("/api/v1/ingestion/benchmark")
@SuppressWarnings("null")
public class BenchmarkIngestionController {

    // Phase 2H canonical analysis window
    private static final LocalDate CANONICAL_START = LocalDate.of(2021, 1, 1);
    private static final LocalDate CANONICAL_END   = LocalDate.of(2024, 1, 15);

    private final NiftyBenchmarkIngestionService benchmarkIngestionService;
    private final BenchmarkDataFreshnessService freshnessService;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;

    public BenchmarkIngestionController(
        NiftyBenchmarkIngestionService benchmarkIngestionService,
        BenchmarkDataFreshnessService freshnessService
    ) {
        this(benchmarkIngestionService, freshnessService, null, null);
    }

    @Autowired
    public BenchmarkIngestionController(
        NiftyBenchmarkIngestionService benchmarkIngestionService,
        BenchmarkDataFreshnessService freshnessService,
        @Autowired(required = false) BenchmarkRepository benchmarkRepository,
        @Autowired(required = false) BenchmarkObservationRepository benchmarkObservationRepository
    ) {
        this.benchmarkIngestionService = benchmarkIngestionService;
        this.freshnessService = freshnessService;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
    }

    /**
     * Executes controlled production benchmark data refresh up to specified endDate (default current date).
     *
     * POST /api/v1/ingestion/benchmark/refresh?startDate=2021-01-01&endDate=2026-10-04
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshBenchmarkData(
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = CANONICAL_START;
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            NiftyIngestionSummary summary = benchmarkIngestionService.refreshBenchmarkData(startDate, endDate);
            BenchmarkDataFreshnessDto freshness = freshnessService.getFreshnessStatus();

            return ResponseEntity.ok(Map.of(
                "benchmarkCode", NiftyBenchmarkIngestionService.NIFTY_500_TRI_CODE,
                "benchmarkName", NiftyBenchmarkIngestionService.NIFTY_500_TRI_NAME,
                "provider", NiftyBenchmarkIngestionService.NSE_PROVIDER,
                "requestedWindow", Map.of("startDate", startDate.toString(), "endDate", endDate.toString()),
                "ingestionSummary", Map.of(
                    "totalParsed", summary.totalParsed(),
                    "insertedCount", summary.insertedCount(),
                    "skippedCount", summary.skippedCount(),
                    "revisedCount", summary.revisedCount(),
                    "sourceArtifactId", summary.sourceArtifactId() != null ? summary.sourceArtifactId() : 0,
                    "payloadSha256", summary.payloadSha256() != null ? summary.payloadSha256() : "N/A"
                ),
                "freshness", freshness,
                "epistemicStatus", summary.totalParsed() > 0 ? "BENCHMARK_REFRESH_SUCCESSFUL" : "BENCHMARK_DATA_PRESERVED",
                "governanceNote",
                    "Benchmark data refresh operation executed. " +
                    "Observation data is persisted strictly for quantitative calculation inputs. " +
                    "Benchmark data refresh does NOT calculate fund metrics or score snapshots."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BENCHMARK_REFRESH_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Retrieves the benchmark data freshness and coverage status.
     *
     * GET /api/v1/ingestion/benchmark/freshness
     */
    @GetMapping("/freshness")
    public ResponseEntity<BenchmarkDataFreshnessDto> getFreshnessStatus() {
        return ResponseEntity.ok(freshnessService.getFreshnessStatus());
    }

    /**
     * Part E: Ingest NIFTY 500 TRI for the canonical Phase 2H analysis window
     * (2021-01-01 → 2024-01-15) as required for benchmark-relative MetricResults.
     *
     * POST /api/v1/ingestion/benchmark/nifty500-tri
     */
    @PostMapping("/nifty500-tri")
    public ResponseEntity<?> ingestNifty500Tri() {
        return ingestNifty500TriRange(CANONICAL_START, CANONICAL_END);
    }

    /**
     * Part E (parameterized): Ingest NIFTY 500 TRI for an explicit date range.
     *
     * POST /api/v1/ingestion/benchmark/nifty500-tri/range?startDate=2021-01-01&endDate=2024-01-15
     */
    @PostMapping("/nifty500-tri/range")
    public ResponseEntity<?> ingestNifty500TriRange(
        @RequestParam(value = "startDate", defaultValue = "2021-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", defaultValue = "2024-01-15")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            benchmarkIngestionService.ensureCanonicalBenchmark();
            NiftyIngestionSummary summary = benchmarkIngestionService.ingestRange(startDate, endDate);

            return ResponseEntity.ok(Map.of(
                "benchmarkCode", NiftyBenchmarkIngestionService.NIFTY_500_TRI_CODE,
                "benchmarkName", NiftyBenchmarkIngestionService.NIFTY_500_TRI_NAME,
                "provider", NiftyBenchmarkIngestionService.NSE_PROVIDER,
                "requestedWindow", Map.of("startDate", startDate.toString(), "endDate", endDate.toString()),
                "ingestionSummary", Map.of(
                    "totalParsed",    summary.totalParsed(),
                    "insertedCount",  summary.insertedCount(),
                    "skippedCount",   summary.skippedCount(),
                    "revisedCount",   summary.revisedCount(),
                    "sourceArtifactId", summary.sourceArtifactId() != null ? summary.sourceArtifactId() : 0,
                    "payloadSha256",  summary.payloadSha256() != null ? summary.payloadSha256() : "N/A"
                ),
                "epistemicStatus", "BENCHMARK_SERIES_LOADED",
                "governanceNote",
                    "NIFTY 500 TRI observations loaded and cryptographically anchored. " +
                    "Loaded data is a necessary prerequisite for Alpha, Beta, and Tracking Error calculations. " +
                    "DATA LOADED != METHODOLOGY VALIDATED != APPROVED FOR LIVE SCORING."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BENCHMARK_INGESTION_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Part F: Retrieves the current coverage status of the NIFTY 500 TRI benchmark series.
     *
     * GET /api/v1/ingestion/benchmark/nifty500-tri/status
     */
    @GetMapping("/nifty500-tri/status")
    public ResponseEntity<?> getNifty500TriStatus() {
        try {
            var benchmark = benchmarkIngestionService.ensureCanonicalBenchmark();
            var freshness = freshnessService.getFreshnessStatus();
            return ResponseEntity.ok(Map.of(
                "benchmarkCode", benchmark.getCode(),
                "benchmarkName", benchmark.getName(),
                "provider",      benchmark.getProvider(),
                "returnVariant", benchmark.getReturnVariant(),
                "registrationStatus", "CANONICAL_BENCHMARK_REGISTERED",
                "freshness", freshness,
                "governanceNote",
                    "Canonical NIFTY 500 TRI benchmark entity is registered. " +
                    "Query BenchmarkObservationRepository for actual observation counts and date coverage."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error",   "BENCHMARK_STATUS_CHECK_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Retrieves the status of the five canonical category benchmarks for the reference universe (TSK-M1-04).
     *
     * GET /api/v1/ingestion/benchmark/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategoryBenchmarksStatus() {
        benchmarkIngestionService.ensureCategoryBenchmarks();
        List<Map<String, Object>> categoriesList = new ArrayList<>();
        for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category", desc.category().name());
            item.put("categoryDisplayName", desc.category().getDisplayName());
            item.put("benchmarkCode", desc.benchmarkCode());
            item.put("benchmarkName", desc.benchmarkName());
            item.put("provider", desc.provider());
            item.put("returnVariant", desc.returnVariant());
            item.put("isTri", desc.isTri());
            item.put("methodologyStatus", desc.methodologyStatus());
            item.put("rationalization", desc.rationalization());

            if (benchmarkRepository != null && benchmarkObservationRepository != null) {
                var bmOpt = benchmarkRepository.findByCode(desc.benchmarkCode());
                if (bmOpt.isPresent()) {
                    Long bmId = bmOpt.get().getId();
                    var obs = benchmarkObservationRepository.findByBenchmarkId(bmId);
                    item.put("totalObservations", obs.size());
                    item.put("earliestDate", benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bmId).orElse(null));
                    item.put("latestDate", benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(bmId).orElse(null));
                    item.put("coverageStatus", obs.size() >= 700 ? "3Y_COVERAGE_SUFFICIENT" : (obs.size() > 0 ? "PARTIAL_COVERAGE" : "NO_OBSERVATIONS"));
                } else {
                    item.put("totalObservations", 0);
                    item.put("coverageStatus", "NOT_REGISTERED");
                }
            }

            categoriesList.add(item);
        }

        return ResponseEntity.ok(Map.of(
            "ruleVersion", CategoryBenchmarkMapping.RULE_VERSION,
            "governanceNote", "Category benchmark mappings represent candidate methodology conventions under Phase 2H. Total Return Index (TRI) variant is mandatory.",
            "categories", categoriesList
        ));
    }

    /**
     * Operator-invoked endpoint to backfill any canonical category benchmark series (TSK-M1-04 / TSK-M1-07).
     *
     * POST /api/v1/ingestion/benchmark/categories/{benchmarkCode}/backfill
     */
    @PostMapping("/categories/{benchmarkCode}/backfill")
    public ResponseEntity<?> backfillCategoryBenchmark(
        @PathVariable String benchmarkCode,
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        if (descOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "UNKNOWN_BENCHMARK",
                "message", "Benchmark code '" + benchmarkCode + "' is not one of the authorized canonical category benchmarks."
            ));
        }

        if (startDate == null) {
            startDate = CANONICAL_START;
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            var audit = benchmarkIngestionService.ingestCategoryBenchmarkWithAudit(benchmarkCode, startDate, endDate);
            boolean isSuccess = "SUCCESS".equalsIgnoreCase(audit.status()) || "PARTIAL".equalsIgnoreCase(audit.status());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("benchmarkCode", audit.benchmarkCode());
            response.put("benchmarkName", audit.benchmarkName());
            response.put("provider", audit.provider());
            response.put("returnVariant", audit.returnVariant());
            response.put("category", descOpt.get().category().name());
            response.put("methodologyStatus", descOpt.get().methodologyStatus());
            response.put("requestedWindow", Map.of(
                "startDate", audit.requestedStartDate() != null ? audit.requestedStartDate().toString() : startDate.toString(),
                "endDate", audit.requestedEndDate() != null ? audit.requestedEndDate().toString() : endDate.toString()
            ));
            response.put("actualReturnedWindow", Map.of(
                "minDate", audit.actualMinDate() != null ? audit.actualMinDate().toString() : "N/A",
                "maxDate", audit.actualMaxDate() != null ? audit.actualMaxDate().toString() : "N/A"
            ));
            response.put("auditMetrics", Map.of(
                "rowsReceived", audit.rowsReceived(),
                "rowsAccepted", audit.rowsAccepted(),
                "rowsRejected", audit.rowsRejected(),
                "rowsInserted", audit.rowsInserted(),
                "rowsRevised", audit.rowsRevised(),
                "duplicatesSkipped", audit.duplicatesSkipped()
            ));
            response.put("sourceArtifact", Map.of(
                "sourceArtifactId", audit.sourceArtifactId() != null ? audit.sourceArtifactId() : 0,
                "payloadSha256", audit.payloadSha256() != null ? audit.payloadSha256() : "N/A"
            ));
            response.put("epistemicStatus", isSuccess ? "BENCHMARK_BACKFILL_SUCCESSFUL" : "BENCHMARK_FETCH_FAILED");
            response.put("failureReason", audit.failureReason() != null ? audit.failureReason() : "");
            response.put("governanceNote", "Category benchmark ingested. Raw payload cryptographically anchored in SourceArtifact. Candidate mapping convention preserved.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BENCHMARK_INGESTION_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Operator-invoked endpoint to backfill all missing or incomplete canonical category benchmarks (TSK-M1-07).
     * Bounded and idempotent: benchmarks already meeting the sufficiency threshold are skipped without re-downloading.
     * Failures are isolated per benchmark.
     *
     * POST /api/v1/ingestion/benchmark/categories/backfill-all
     */
    @PostMapping("/categories/backfill-all")
    public ResponseEntity<?> backfillAllCategoryBenchmarks(
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(value = "minThreshold", defaultValue = "700") int minThreshold
    ) {
        if (startDate == null) {
            startDate = CANONICAL_START;
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            var results = benchmarkIngestionService.ingestAllMissingCategoryBenchmarks(startDate, endDate, minThreshold);

            int totalReceived = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::rowsReceived).sum();
            int totalAccepted = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::rowsAccepted).sum();
            int totalInserted = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::rowsInserted).sum();
            int totalRevised = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::rowsRevised).sum();
            int totalSkipped = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::duplicatesSkipped).sum();

            long successfulCount = results.stream().filter(r -> "SUCCESS".equalsIgnoreCase(r.status()) || "SKIPPED_ALREADY_SUFFICIENT".equalsIgnoreCase(r.status())).count();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operation", "BACKFILL_ALL_CATEGORY_BENCHMARKS");
            response.put("requestedWindow", Map.of("startDate", startDate.toString(), "endDate", endDate.toString()));
            response.put("benchmarksEvaluated", results.size());
            response.put("benchmarksSufficientOrSuccess", successfulCount);
            response.put("aggregateAuditMetrics", Map.of(
                "totalReceived", totalReceived,
                "totalAccepted", totalAccepted,
                "totalInserted", totalInserted,
                "totalRevised", totalRevised,
                "totalSkipped", totalSkipped
            ));
            response.put("benchmarkResults", results);
            response.put("epistemicStatus", successfulCount == results.size() ? "ALL_CATEGORY_BENCHMARKS_READY" : "PARTIAL_BENCHMARK_COVERAGE");
            response.put("governanceNote", "All 5 reference-universe category benchmarks processed with failure isolation. Candidate methodology status strictly preserved.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BATCH_BENCHMARK_BACKFILL_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Executes incremental refresh for all five canonical category benchmarks up to referenceDate (Phase A2).
     *
     * POST /api/v1/ingestion/benchmark/refresh-all
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<?> refreshAllCategoryBenchmarks(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        try {
            var results = benchmarkIngestionService.refreshAllCategoryBenchmarks(evalDate);
            var freshnessMap = freshnessService.getAllCategoryBenchmarksFreshness(evalDate);

            int totalInserted = results.stream().mapToInt(NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult::rowsInserted).sum();
            long successCount = results.stream().filter(r -> "SUCCESS".equalsIgnoreCase(r.status()) || "UP_TO_DATE".equalsIgnoreCase(r.status())).count();

            return ResponseEntity.ok(Map.of(
                "operation", "REFRESH_ALL_CATEGORY_BENCHMARKS",
                "referenceDate", evalDate.toString(),
                "benchmarksEvaluated", results.size(),
                "benchmarksCurrent", successCount,
                "totalObservationsInserted", totalInserted,
                "benchmarkResults", results,
                "freshnessSummary", freshnessMap,
                "status", (successCount == results.size()) ? "ALL_BENCHMARKS_CURRENT" : "PARTIAL_REFRESH",
                "governanceNote", "Daily category benchmark incremental refresh executed. Candidate methodology mappings preserved."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BENCHMARK_REFRESH_ALL_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Retrieves freshness across all five canonical category benchmarks for the reference universe (Phase A2).
     *
     * GET /api/v1/ingestion/benchmark/categories/freshness
     */
    @GetMapping("/categories/freshness")
    public ResponseEntity<?> getAllCategoriesFreshness(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        return ResponseEntity.ok(Map.of(
            "referenceDate", evalDate.toString(),
            "ruleVersion", CategoryBenchmarkMapping.RULE_VERSION,
            "freshnessByCategoryBenchmark", freshnessService.getAllCategoryBenchmarksFreshness(evalDate),
            "governanceNote", "Freshness reflects observation availability against market trading calendar. Data availability != methodology validation."
        ));
    }

    /**
     * Retrieves factual multi-horizon historical TRI coverage (3Y, 5Y, 10Y) across all five
     * canonical reference category benchmarks (Phase A4).
     *
     * GET /api/v1/ingestion/benchmark/categories/horizon-coverage?referenceDate=2026-10-01&horizon=10Y
     */
    @GetMapping("/categories/horizon-coverage")
    public ResponseEntity<?> getCategoryBenchmarksHorizonCoverage(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
        @RequestParam(value = "horizon", required = false) String horizon
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (horizon != null && !horizon.isBlank() && !"ALL".equalsIgnoreCase(horizon.trim())) {
            AnalyticalHorizon h = AnalyticalHorizon.fromCode(horizon);
            List<BenchmarkHorizonCoverageDto> list = new ArrayList<>();
            for (var desc : CategoryBenchmarkMapping.getAllDescriptors()) {
                list.add(benchmarkIngestionService.evaluateBenchmarkHorizon(desc.benchmarkCode(), h, evalDate));
            }
            return ResponseEntity.ok(Map.of(
                "referenceDate", evalDate.toString(),
                "horizon", h.getCode(),
                "ruleVersion", CategoryBenchmarkMapping.RULE_VERSION,
                "benchmarks", list,
                "governanceNote", NiftyBenchmarkIngestionService.GOVERNANCE_DISCLAIMER
            ));
        }

        CategoryBenchmarksMultiHorizonReportDto report = benchmarkIngestionService.evaluateAllCategoryBenchmarksMultiHorizon(evalDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Retrieves factual multi-horizon historical TRI coverage (3Y, 5Y, 10Y) for an individual benchmark (Phase A4).
     *
     * GET /api/v1/ingestion/benchmark/{benchmarkCode}/horizon-coverage?referenceDate=2026-10-01&horizon=5Y
     */
    @GetMapping("/{benchmarkCode}/horizon-coverage")
    public ResponseEntity<?> getBenchmarkHorizonCoverage(
        @PathVariable String benchmarkCode,
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
        @RequestParam(value = "horizon", required = false) String horizon
    ) {
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        if (descOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "UNKNOWN_BENCHMARK",
                "message", "Benchmark code '" + benchmarkCode + "' is not one of the authorized canonical category benchmarks."
            ));
        }

        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (horizon != null && !horizon.isBlank() && !"ALL".equalsIgnoreCase(horizon.trim())) {
            AnalyticalHorizon h = AnalyticalHorizon.fromCode(horizon);
            BenchmarkHorizonCoverageDto cov = benchmarkIngestionService.evaluateBenchmarkHorizon(benchmarkCode, h, evalDate);
            return ResponseEntity.ok(cov);
        }

        BenchmarkMultiHorizonCoverageDto multi = benchmarkIngestionService.evaluateBenchmarkMultiHorizon(benchmarkCode, evalDate);
        return ResponseEntity.ok(multi);
    }

    /**
     * Executes bounded historical recovery for a specific canonical category benchmark in an analytical horizon (Phase A4).
     *
     * POST /api/v1/ingestion/benchmark/{benchmarkCode}/recovery/horizon
     */
    @PostMapping("/{benchmarkCode}/recovery/horizon")
    public ResponseEntity<?> recoverBenchmarkHorizon(
        @PathVariable String benchmarkCode,
        @RequestParam(value = "horizon", defaultValue = "3Y") String horizon,
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
        @RequestParam(value = "maxChunks", required = false, defaultValue = "10") Integer maxChunks,
        @RequestParam(value = "maxChunkDays", required = false, defaultValue = "365") Integer maxChunkDays,
        @RequestBody(required = false) BenchmarkHorizonRecoveryRequest requestBody
    ) {
        var descOpt = CategoryBenchmarkMapping.getDescriptorByCode(benchmarkCode);
        if (descOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "UNKNOWN_BENCHMARK",
                "message", "Benchmark code '" + benchmarkCode + "' is not one of the authorized canonical category benchmarks."
            ));
        }

        String effHorizon = (requestBody != null && requestBody.horizon() != null && !requestBody.horizon().isBlank())
            ? requestBody.horizon() : horizon;
        LocalDate effRefDate = (requestBody != null && requestBody.referenceDate() != null)
            ? requestBody.referenceDate() : (referenceDate != null ? referenceDate : LocalDate.now());
        int effMaxChunks = (requestBody != null && requestBody.maxChunksPerBenchmark() != null)
            ? requestBody.resolvedMaxChunks() : (maxChunks != null ? maxChunks : 10);
        int effMaxChunkDays = (requestBody != null && requestBody.maxChunkDays() != null)
            ? requestBody.resolvedMaxChunkDays() : (maxChunkDays != null ? maxChunkDays : 365);

        AnalyticalHorizon h = AnalyticalHorizon.fromCode(effHorizon);

        try {
            BenchmarkHorizonRecoveryResultDto result = benchmarkIngestionService.recoverBenchmarkHorizon(
                benchmarkCode, h, effRefDate, effMaxChunks, effMaxChunkDays
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "BENCHMARK_HORIZON_RECOVERY_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Executes bounded historical recovery across all five canonical category benchmarks in an analytical horizon (Phase A4).
     *
     * POST /api/v1/ingestion/benchmark/categories/recovery/horizon
     */
    @PostMapping("/categories/recovery/horizon")
    public ResponseEntity<?> recoverAllCategoryBenchmarksHorizon(
        @RequestParam(value = "horizon", defaultValue = "3Y") String horizon,
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
        @RequestParam(value = "maxChunksPerBenchmark", required = false, defaultValue = "10") Integer maxChunksPerBenchmark,
        @RequestParam(value = "maxChunkDays", required = false, defaultValue = "365") Integer maxChunkDays,
        @RequestBody(required = false) BenchmarkHorizonRecoveryRequest requestBody
    ) {
        String effHorizon = (requestBody != null && requestBody.horizon() != null && !requestBody.horizon().isBlank())
            ? requestBody.horizon() : horizon;
        LocalDate effRefDate = (requestBody != null && requestBody.referenceDate() != null)
            ? requestBody.referenceDate() : (referenceDate != null ? referenceDate : LocalDate.now());
        int effMaxChunks = (requestBody != null && requestBody.maxChunksPerBenchmark() != null)
            ? requestBody.resolvedMaxChunks() : (maxChunksPerBenchmark != null ? maxChunksPerBenchmark : 10);
        int effMaxChunkDays = (requestBody != null && requestBody.maxChunkDays() != null)
            ? requestBody.resolvedMaxChunkDays() : (maxChunkDays != null ? maxChunkDays : 365);

        AnalyticalHorizon h = AnalyticalHorizon.fromCode(effHorizon);

        try {
            CategoryBenchmarksHorizonRecoveryReportDto report = benchmarkIngestionService.recoverAllCategoryBenchmarksHorizon(
                h, effRefDate, effMaxChunks, effMaxChunkDays
            );
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "CATEGORY_BENCHMARKS_RECOVERY_FAILED",
                "message", e.getMessage()
            ));
        }
    }
}

