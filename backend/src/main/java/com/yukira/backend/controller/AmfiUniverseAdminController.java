package com.yukira.backend.controller;

import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseCoverageReport;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST Controller for authenticated administrative / operational AMFI universe ingestion actions.
 *
 * Security & Governance Contract:
 * 1. Endpoint path: /api/v1/admin/ingestion/universe
 * 2. Protection: Requires OAuth2 authentication via SecurityConfig matchers (.requestMatchers("/api/v1/admin/**").authenticated()).
 * 3. Scope: Executes official AMFI universe scheme-master ingestion and retrieves coverage metrics.
 * 4. Safety: No anonymous unauthenticated execution; adheres strictly to the security guardrail.
 */
@RestController
@RequestMapping("/api/v1/admin/ingestion/universe")
public class AmfiUniverseAdminController {

    private final AmfiUniverseIngestionService universeIngestionService;
    private final HistoricalNavIngestionService historicalNavIngestionService;
    private final com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService;

    private final com.yukira.backend.universe.UniverseBatchOrchestratorService universeBatchOrchestratorService;
    private final com.yukira.backend.universe.ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService;

    @Autowired
    public AmfiUniverseAdminController(
        AmfiUniverseIngestionService universeIngestionService,
        @Autowired(required = false) HistoricalNavIngestionService historicalNavIngestionService,
        @Autowired(required = false) com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService,
        @Autowired(required = false) com.yukira.backend.universe.UniverseBatchOrchestratorService universeBatchOrchestratorService,
        @Autowired(required = false) com.yukira.backend.universe.ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService
    ) {
        this.universeIngestionService = universeIngestionService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.referenceUniverseService = referenceUniverseService;
        this.universeBatchOrchestratorService = universeBatchOrchestratorService;
        this.referenceUniverseNavRecoveryService = referenceUniverseNavRecoveryService;
    }

    public AmfiUniverseAdminController(
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService,
        com.yukira.backend.universe.UniverseBatchOrchestratorService universeBatchOrchestratorService
    ) {
        this(universeIngestionService, historicalNavIngestionService, referenceUniverseService, universeBatchOrchestratorService, null);
    }

    public AmfiUniverseAdminController(
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService
    ) {
        this(universeIngestionService, historicalNavIngestionService, referenceUniverseService, null, null);
    }

    /**
     * Triggers an immediate operator refresh of the AMFI daily scheme catalog (NAVAll.txt).
     *
     * POST /api/v1/admin/ingestion/universe/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<UniverseIngestionSummary> refreshLiveUniverse() {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        UniverseIngestionSummary summary = universeIngestionService.fetchAndIngestLiveUniverse();
        return ResponseEntity.ok(summary);
    }

    /**
     * Ingests an operator-provided raw AMFI scheme text payload.
     *
     * POST /api/v1/admin/ingestion/universe/payload
     */
    @PostMapping("/payload")
    public ResponseEntity<UniverseIngestionSummary> ingestPayload(@RequestBody String payload) {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        if (payload == null || payload.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        UniverseIngestionSummary summary = universeIngestionService.ingestUniversePayload(bytes, "admin://manual-upload");
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves Part G machine-readable universe catalog coverage, data-quality findings, and historical readiness.
     *
     * GET /api/v1/admin/ingestion/universe/coverage
     */
    @GetMapping("/coverage")
    public ResponseEntity<UniverseCoverageReport> getUniverseCoverage() {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        Map<String, Object> controlledCoverage = historicalNavIngestionService != null
            ? historicalNavIngestionService.getControlledSampleCoverage()
            : Map.of();
        return ResponseEntity.ok(universeIngestionService.generateUniverseCoverageReport(controlledCoverage));
    }

    /**
     * Retrieves the machine-readable reference universe denominator and 5-category breakdown (TSK-M1-02).
     *
     * GET /api/v1/admin/ingestion/universe/reference-report
     */
    @GetMapping("/reference-report")
    public ResponseEntity<com.yukira.backend.universe.ReferenceUniverseReportDto> getReferenceUniverseReport() {
        if (referenceUniverseService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(referenceUniverseService.generateUniverseReport());
    }

    /**
     * Triggers a controlled, idempotent historical NAV backfill specifically for reference-universe candidates (TSK-M1-03).
     *
     * POST /api/v1/admin/ingestion/universe/backfill
     */
    @PostMapping("/backfill")
    public ResponseEntity<com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto> backfillReferenceUniverse(
        @RequestParam(defaultValue = "10") int batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
        @RequestParam(defaultValue = "false") boolean dryRun
    ) {
        if (historicalNavIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        java.time.LocalDate start = startDate != null ? startDate : java.time.LocalDate.now().minusYears(3);
        java.time.LocalDate end = endDate != null ? endDate : java.time.LocalDate.now();
        return ResponseEntity.ok(historicalNavIngestionService.executeReferenceUniverseBackfill(batchSize, category, start, end, dryRun));
    }

    /**
     * Previews a bounded reference-universe batch expansion without network or database mutations (TSK-M1-06).
     *
     * GET /api/v1/admin/ingestion/universe/batch-run/preview
     */
    @GetMapping("/batch-run/preview")
    public ResponseEntity<com.yukira.backend.universe.UniverseBatchRunReportDto> previewBatchRun(
        @RequestParam(defaultValue = "5") int batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.UniverseBatchRunRequest req = new com.yukira.backend.universe.UniverseBatchRunRequest(
            batchSize, category, startDate, endDate, asOfDate, null, true, true, true, true, "admin-preview"
        );
        return ResponseEntity.ok(universeBatchOrchestratorService.previewBatchRun(req));
    }

    /**
     * Triggers an operator-bounded reference universe batch ingestion and automated metric calculation run (TSK-M1-06).
     *
     * POST /api/v1/admin/ingestion/universe/batch-run
     */
    @PostMapping("/batch-run")
    public ResponseEntity<com.yukira.backend.universe.UniverseBatchRunReportDto> executeBatchRun(
        @RequestBody(required = false) com.yukira.backend.universe.UniverseBatchRunRequest request,
        @RequestParam(required = false) Integer batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) Boolean dryRun
    ) {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }

        com.yukira.backend.universe.UniverseBatchRunRequest effectiveReq = request;
        if (effectiveReq == null) {
            int bSize = batchSize != null ? batchSize : 5;
            boolean isDry = dryRun != null ? dryRun : false;
            effectiveReq = new com.yukira.backend.universe.UniverseBatchRunRequest(
                bSize, category, startDate, endDate, asOfDate, null, isDry, true, true, true, "admin-operator"
            );
        }

        return ResponseEntity.ok(universeBatchOrchestratorService.executeBatchRun(effectiveReq));
    }

    /**
     * Retrieves the comprehensive universe data coverage status across the 5 reference categories
     * and their candidate category benchmarks (TSK-M1-06).
     *
     * GET /api/v1/admin/ingestion/universe/coverage-status
     */
    @GetMapping("/coverage-status")
    public ResponseEntity<com.yukira.backend.universe.UniverseCoverageStatusDto> getCoverageStatus() {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(universeBatchOrchestratorService.getUniverseCoverageStatus());
    }

    /**
     * Executes calculation orchestration directly for all eligible (history-sufficient) options
     * in a category without performing external network ingestion (TSK-M1-06).
     *
     * POST /api/v1/admin/ingestion/universe/calculate-eligible
     */
    @PostMapping("/calculate-eligible")
    public ResponseEntity<com.yukira.backend.universe.UniverseBatchRunReportDto> calculateEligibleCohort(
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(universeBatchOrchestratorService.calculateEligibleCohort(category, limit, asOfDate, null));
    }

    /**
     * Triggers a bounded, resumable background batch calculation job (TSK-M1-09).
     *
     * POST /api/v1/admin/ingestion/universe/batch-run/async
     */
    @PostMapping("/batch-run/async")
    public ResponseEntity<?> triggerAsyncBatchRun(
        @RequestBody(required = false) com.yukira.backend.universe.UniverseBatchRunRequest request,
        @RequestParam(required = false) Integer batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) Boolean forceRecalculate
    ) {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }

        com.yukira.backend.universe.UniverseBatchRunRequest effectiveReq = request;
        if (effectiveReq == null) {
            int bSize = batchSize != null ? batchSize : 5;
            boolean force = forceRecalculate != null ? forceRecalculate : false;
            effectiveReq = new com.yukira.backend.universe.UniverseBatchRunRequest(
                bSize, category, startDate, endDate, asOfDate, null, false, false, false, true, "admin-async-operator", force
            );
        }

        try {
            com.yukira.backend.universe.UniverseBatchJobStatusDto job = universeBatchOrchestratorService.triggerAsyncBatchRun(effectiveReq);
            return ResponseEntity.accepted().body(job);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of(
                "error", "CONCURRENT_JOB_RUNNING",
                "message", e.getMessage(),
                "activeJob", universeBatchOrchestratorService.getJobStatus()
            ));
        }
    }

    /**
     * Retrieves the progress and terminal status of the current or last completed background batch job (TSK-M1-09).
     *
     * GET /api/v1/admin/ingestion/universe/batch-run/status
     */
    @GetMapping("/batch-run/status")
    public ResponseEntity<com.yukira.backend.universe.UniverseBatchJobStatusDto> getBatchRunStatus() {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(universeBatchOrchestratorService.getJobStatus());
    }

    /**
     * Retrieves the empirical 5-way historical NAV recovery inventory across all 193 reference options (Phase A1).
     *
     * GET /api/v1/admin/ingestion/universe/recovery/inventory
     */
    @GetMapping("/recovery/inventory")
    public ResponseEntity<com.yukira.backend.universe.ReferenceUniverseRecoveryInventoryDto> getRecoveryInventory(
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.getInventory(startDate, endDate));
    }

    /**
     * Executes bounded stored-artifact historical NAV recovery reusing stored authentic artifacts #5378–#5381 (Phase A1).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/stored-artifacts
     */
    @PostMapping("/recovery/stored-artifacts")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryBatchExecutionResultDto> executeStoredArtifactRecovery(
        @RequestBody(required = false) com.yukira.backend.universe.StoredArtifactRecoveryRequest request,
        @RequestParam(required = false) Integer batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) Boolean recalculateProfiles,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.StoredArtifactRecoveryRequest effectiveReq = request;
        if (effectiveReq == null) {
            effectiveReq = new com.yukira.backend.universe.StoredArtifactRecoveryRequest(
                batchSize, category, null, recalculateProfiles, asOfDate, "admin-operator"
            );
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.executeStoredArtifactRecovery(effectiveReq));
    }

    /**
     * Executes bounded targeted historical interval recovery for options with gaps in the 3Y window (Phase A1).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/targeted-intervals
     */
    @PostMapping("/recovery/targeted-intervals")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryBatchExecutionResultDto> executeTargetedIntervalRecovery(
        @RequestBody(required = false) com.yukira.backend.universe.TargetedIntervalRecoveryRequest request,
        @RequestParam(required = false) Integer batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) Boolean recalculateProfiles,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) Integer maxIntervalsPerFund
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.TargetedIntervalRecoveryRequest effectiveReq = request;
        if (effectiveReq == null) {
            effectiveReq = new com.yukira.backend.universe.TargetedIntervalRecoveryRequest(
                batchSize, category, null, recalculateProfiles, asOfDate, maxIntervalsPerFund, "admin-operator"
            );
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.executeTargetedIntervalRecovery(effectiveReq));
    }

    /**
     * Operationally recovers missing intervals for a specific scheme option by ID (Phase A2).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/option/{schemeOptionId}
     */
    @PostMapping("/recovery/option/{schemeOptionId}")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryFundOutcomeDto> recoverOptionIntervalsById(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate windowStart,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate windowEnd,
        @RequestParam(defaultValue = "10") int maxIntervals,
        @RequestParam(defaultValue = "true") boolean triggerCalculation
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.recoverOptionIntervals(
            schemeOptionId, windowStart, windowEnd, null, maxIntervals, triggerCalculation
        ));
    }

    /**
     * Operationally recovers missing intervals for a specific fund by AMFI scheme code (Phase A2).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/amfi/{amfiCode}
     */
    @PostMapping("/recovery/amfi/{amfiCode}")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryFundOutcomeDto> recoverOptionByAmfiCode(
        @PathVariable String amfiCode,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate windowStart,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate windowEnd,
        @RequestParam(defaultValue = "true") boolean triggerCalculation
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.recoverOptionByAmfiCode(
            amfiCode, windowStart, windowEnd, triggerCalculation
        ));
    }

    /**
     * Retrieves factual universe-wide coverage report for an analytical horizon (Phase A3).
     *
     * GET /api/v1/admin/ingestion/universe/recovery/horizon
     */
    @GetMapping("/recovery/horizon")
    public ResponseEntity<com.yukira.backend.universe.UniverseHorizonCoverageReportDto> getHorizonCoverageReport(
        @RequestParam(defaultValue = "5Y") String horizon,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.getHorizonInventory(h, asOfDate));
    }

    /**
     * Executes bounded multi-horizon recovery batch across eligible candidates (Phase A3).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/horizon
     */
    @PostMapping("/recovery/horizon")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryBatchExecutionResultDto> executeHorizonRecovery(
        @RequestBody(required = false) com.yukira.backend.universe.HorizonRecoveryRequest request,
        @RequestParam(required = false) String horizon,
        @RequestParam(required = false) Integer batchSize,
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false) Boolean triggerCalculation,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) Integer maxIntervalsPerFund
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.HorizonRecoveryRequest effectiveReq = request;
        if (effectiveReq == null) {
            com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
            effectiveReq = new com.yukira.backend.universe.HorizonRecoveryRequest(
                h, asOfDate, batchSize, category, null, triggerCalculation, maxIntervalsPerFund, "admin-operator"
            );
        }
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.executeHorizonRecovery(effectiveReq));
    }

    /**
     * Operationally recovers missing intervals for a specific scheme option under an analytical horizon (Phase A3).
     *
     * POST /api/v1/admin/ingestion/universe/recovery/option/{schemeOptionId}/horizon
     */
    @PostMapping("/recovery/option/{schemeOptionId}/horizon")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryFundOutcomeDto> recoverOptionHorizonById(
        @PathVariable Long schemeOptionId,
        @RequestParam(defaultValue = "5Y") String horizon,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(defaultValue = "10") int maxIntervals,
        @RequestParam(defaultValue = "true") boolean triggerCalculation
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.recoverOptionHorizon(
            schemeOptionId, h, asOfDate, maxIntervals, triggerCalculation
        ));
    }
}
