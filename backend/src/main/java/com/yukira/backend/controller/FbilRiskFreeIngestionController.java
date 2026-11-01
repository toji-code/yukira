package com.yukira.backend.controller;

import com.yukira.backend.dto.ingestion.FbilDataFreshnessDto;
import com.yukira.backend.ingestion.fbil.FbilTBillIngestionService;
import com.yukira.backend.ingestion.fbil.FbilTBillIngestionService.FbilIngestionSummary;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.riskfree.RiskFreeHorizonCoverageDto;
import com.yukira.backend.riskfree.RiskFreeHorizonRecoveryRequest;
import com.yukira.backend.riskfree.RiskFreeHorizonRecoveryResultDto;
import com.yukira.backend.riskfree.RiskFreeMultiHorizonCoverageDto;
import com.yukira.backend.service.ingestion.FbilDataFreshnessService;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for FBIL 91-Day Treasury Bill risk-free rate ingestion and freshness reporting (Phase A2).
 *
 * Epistemic & Methodological Guardrails:
 * 1. Rates are persisted with authentic cryptographic SHA-256 provenance via SourceArtifact.
 * 2. FBIL quoted yields are published under ACT/365 daycount convention.
 * 3. Idempotent repeated execution: duplicates are skipped, revisions tracked.
 * 4. Zero synthetic rates or forward-filling of missing dates.
 * 5. Rate ingestion does NOT calculate fund risk or performance metrics.
 */
@RestController
@RequestMapping("/api/v1/ingestion/fbil")
public class FbilRiskFreeIngestionController {

    private static final LocalDate CANONICAL_START = LocalDate.of(2021, 1, 1);

    private final FbilTBillIngestionService fbilTBillIngestionService;
    private final FbilDataFreshnessService fbilDataFreshnessService;
    private final RiskFreeObservationRepository riskFreeObservationRepository;

    public FbilRiskFreeIngestionController(
        FbilTBillIngestionService fbilTBillIngestionService,
        FbilDataFreshnessService fbilDataFreshnessService,
        RiskFreeObservationRepository riskFreeObservationRepository
    ) {
        this.fbilTBillIngestionService = fbilTBillIngestionService;
        this.fbilDataFreshnessService = fbilDataFreshnessService;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
    }

    /**
     * Executes incremental FBIL risk-free rate refresh up to referenceDate (Phase A2).
     *
     * POST /api/v1/ingestion/fbil/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshRates(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        try {
            FbilIngestionSummary summary = fbilTBillIngestionService.refreshLatestRates(evalDate);
            FbilDataFreshnessDto freshness = fbilDataFreshnessService.getFreshnessStatus(evalDate);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operation", "FBIL_RATE_REFRESH");
            response.put("benchmarkCode", FbilDataFreshnessService.FBIL_91D_TBILL_CODE);
            response.put("provider", FbilDataFreshnessService.FBIL_PROVIDER);
            response.put("daycountConvention", FbilDataFreshnessService.DAYCOUNT_ACT_365);
            response.put("referenceDate", evalDate.toString());
            response.put("ingestionSummary", Map.of(
                "totalParsed", summary.totalParsed(),
                "insertedCount", summary.insertedCount(),
                "skippedCount", summary.skippedCount(),
                "revisedCount", summary.revisedCount(),
                "sourceArtifactId", summary.sourceArtifactId() != null ? summary.sourceArtifactId() : 0,
                "payloadSha256", summary.payloadSha256() != null ? summary.payloadSha256() : "N/A",
                "status", summary.status() != null ? summary.status() : "SUCCESS",
                "message", summary.message() != null ? summary.message() : ""
            ));
            response.put("freshness", freshness);
            response.put("governanceNote", FbilDataFreshnessService.GOVERNANCE_DISCLAIMER);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "FBIL_REFRESH_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Executes bounded FBIL rate range ingestion.
     *
     * POST /api/v1/ingestion/fbil/range?startDate=2021-01-01&endDate=2026-10-01
     */
    @PostMapping("/range")
    public ResponseEntity<?> ingestRange(
        @RequestParam(value = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : CANONICAL_START;
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        if (start.isAfter(end)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            FbilIngestionSummary summary = fbilTBillIngestionService.ingestRange(start, end);
            FbilDataFreshnessDto freshness = fbilDataFreshnessService.getFreshnessStatus(end);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("operation", "FBIL_RANGE_INGESTION");
            response.put("benchmarkCode", FbilDataFreshnessService.FBIL_91D_TBILL_CODE);
            response.put("requestedWindow", Map.of("startDate", start.toString(), "endDate", end.toString()));
            response.put("ingestionSummary", Map.of(
                "totalParsed", summary.totalParsed(),
                "insertedCount", summary.insertedCount(),
                "skippedCount", summary.skippedCount(),
                "revisedCount", summary.revisedCount(),
                "sourceArtifactId", summary.sourceArtifactId() != null ? summary.sourceArtifactId() : 0,
                "payloadSha256", summary.payloadSha256() != null ? summary.payloadSha256() : "N/A"
            ));
            response.put("freshness", freshness);
            response.put("governanceNote", FbilDataFreshnessService.GOVERNANCE_DISCLAIMER);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "FBIL_RANGE_INGESTION_FAILED",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Retrieves current FBIL risk-free rate data freshness status (Phase A2).
     *
     * GET /api/v1/ingestion/fbil/freshness
     */
    @GetMapping("/freshness")
    public ResponseEntity<FbilDataFreshnessDto> getFreshness(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        return ResponseEntity.ok(fbilDataFreshnessService.getFreshnessStatus(evalDate));
    }

    /**
     * Retrieves summary status of persisted FBIL risk-free observations.
     *
     * GET /api/v1/ingestion/fbil/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        String code = FbilDataFreshnessService.FBIL_91D_TBILL_CODE;
        long total = riskFreeObservationRepository.countByBenchmarkCode(code);
        var minDate = riskFreeObservationRepository.findMinEffectiveDateByBenchmarkCode(code);
        var maxDate = riskFreeObservationRepository.findMaxEffectiveDateByBenchmarkCode(code);
        var maxAvail = riskFreeObservationRepository.findMaxAvailabilityTimeByBenchmarkCode(code);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("benchmarkCode", code);
        response.put("benchmarkName", FbilDataFreshnessService.FBIL_91D_TBILL_NAME);
        response.put("provider", FbilDataFreshnessService.FBIL_PROVIDER);
        response.put("daycountConvention", FbilDataFreshnessService.DAYCOUNT_ACT_365);
        response.put("totalObservations", total);
        response.put("earliestDate", minDate.orElse(null));
        response.put("latestDate", maxDate.orElse(null));
        response.put("latestAvailabilityTime", maxAvail.orElse(null));
        response.put("coverageStatus", total > 0 ? "OBSERVATIONS_AVAILABLE" : "NO_OBSERVATIONS");
        response.put("governanceNote", FbilDataFreshnessService.GOVERNANCE_DISCLAIMER);

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves factual historical risk-free-rate coverage across an explicit horizon (3Y, 5Y, or 10Y) (Phase A5).
     * Side-effect-free read endpoint.
     *
     * GET /api/v1/ingestion/fbil/horizon-coverage?horizon=5Y&referenceDate=2026-10-01
     */
    @GetMapping("/horizon-coverage")
    public ResponseEntity<?> getHorizonCoverage(
        @RequestParam(value = "horizon", required = false) String horizon,
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (horizon != null && !horizon.isBlank() && !"ALL".equalsIgnoreCase(horizon.trim())) {
            AnalyticalHorizon h = AnalyticalHorizon.fromCode(horizon);
            RiskFreeHorizonCoverageDto cov = fbilTBillIngestionService.evaluateRiskFreeHorizon(
                FbilDataFreshnessService.FBIL_91D_TBILL_CODE, h, evalDate
            );
            return ResponseEntity.ok(cov);
        }

        RiskFreeMultiHorizonCoverageDto multi = fbilTBillIngestionService.evaluateRiskFreeMultiHorizon(
            FbilDataFreshnessService.FBIL_91D_TBILL_CODE, evalDate
        );
        return ResponseEntity.ok(multi);
    }

    /**
     * Retrieves factual multi-horizon historical risk-free-rate coverage across 3Y, 5Y, and 10Y horizons (Phase A5).
     * Side-effect-free read endpoint.
     *
     * GET /api/v1/ingestion/fbil/multi-horizon-coverage?referenceDate=2026-10-01
     */
    @GetMapping("/multi-horizon-coverage")
    public ResponseEntity<RiskFreeMultiHorizonCoverageDto> getMultiHorizonCoverage(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        return ResponseEntity.ok(fbilTBillIngestionService.evaluateRiskFreeMultiHorizon(
            FbilDataFreshnessService.FBIL_91D_TBILL_CODE, evalDate
        ));
    }

    /**
     * Executes bounded, chunked historical recovery for FBIL 91D T-Bill in a specific horizon (Phase A5).
     * Reuses stored source artifacts where available. Batches are bounded and execution is resumable.
     *
     * POST /api/v1/ingestion/fbil/recovery/horizon
     */
    @PostMapping("/recovery/horizon")
    public ResponseEntity<RiskFreeHorizonRecoveryResultDto> recoverHorizon(
        @RequestParam(value = "horizon", defaultValue = "3Y") String horizonParam,
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDateParam,
        @RequestParam(value = "maxChunks", required = false, defaultValue = "10") Integer maxChunksParam,
        @RequestParam(value = "maxChunkDays", required = false, defaultValue = "365") Integer maxChunkDaysParam,
        @RequestParam(value = "forceRefresh", required = false, defaultValue = "false") Boolean forceRefreshParam,
        @RequestBody(required = false) RiskFreeHorizonRecoveryRequest requestBody
    ) {
        String horizon = requestBody != null && requestBody.horizon() != null ? requestBody.horizon() : horizonParam;
        LocalDate refDate = requestBody != null && requestBody.referenceDate() != null
            ? requestBody.referenceDate()
            : (referenceDateParam != null ? referenceDateParam : LocalDate.now());
        Integer maxChunks = requestBody != null && requestBody.maxChunks() != null ? requestBody.maxChunks() : maxChunksParam;
        Integer maxChunkDays = requestBody != null && requestBody.maxChunkDays() != null ? requestBody.maxChunkDays() : maxChunkDaysParam;
        Boolean forceRefresh = requestBody != null && requestBody.forceRefresh() != null ? requestBody.forceRefresh() : forceRefreshParam;

        AnalyticalHorizon h = AnalyticalHorizon.fromCode(horizon);
        RiskFreeHorizonRecoveryResultDto result = fbilTBillIngestionService.recoverRiskFreeHorizon(
            FbilDataFreshnessService.FBIL_91D_TBILL_CODE, h, refDate, maxChunks, maxChunkDays, forceRefresh
        );
        return ResponseEntity.ok(result);
    }
}

