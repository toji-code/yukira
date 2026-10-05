package com.yukira.backend.controller;

import com.yukira.backend.dto.ingestion.BenchmarkDataFreshnessDto;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.NiftyIngestionSummary;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API for NIFTY 500 TRI benchmark historical data ingestion and production refresh.
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

    public BenchmarkIngestionController(
        NiftyBenchmarkIngestionService benchmarkIngestionService,
        BenchmarkDataFreshnessService freshnessService
    ) {
        this.benchmarkIngestionService = benchmarkIngestionService;
        this.freshnessService = freshnessService;
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
}

