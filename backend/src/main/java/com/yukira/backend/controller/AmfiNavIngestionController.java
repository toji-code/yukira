package com.yukira.backend.controller;

import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillPreviewDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseCoverageReport;
import com.yukira.backend.service.ingestion.AmfiDataFreshnessService;
import com.yukira.backend.service.ingestion.AmfiNavRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Map;

/**
 * REST Controller for Production AMFI NAV Refresh, Controlled Backfill & Data Freshness operations.
 *
 * Provides controlled, idempotent daily NAV ingestion from official AMFI portal sources,
 * exposes quantitative data freshness indicators, and provides administrative backfill/coverage endpoints.
 *
 * Epistemic constraint: NAV ingestion strictly preserves raw data immutability and provenance.
 * It does NOT execute quant engine calculations or alter historical score snapshots.
 */
@RestController
@RequestMapping("/api/v1/ingestion/amfi")
public class AmfiNavIngestionController {

    private final AmfiNavRefreshService navRefreshService;
    private final AmfiDataFreshnessService dataFreshnessService;
    private final AmfiUniverseIngestionService universeIngestionService;
    private final HistoricalNavIngestionService historicalNavIngestionService;

    private final com.yukira.backend.service.ingestion.AmfiNavRefreshScheduler navRefreshScheduler;

    public AmfiNavIngestionController(
        AmfiNavRefreshService navRefreshService,
        AmfiDataFreshnessService dataFreshnessService,
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.service.ingestion.AmfiNavRefreshScheduler navRefreshScheduler
    ) {
        this.navRefreshService = navRefreshService;
        this.dataFreshnessService = dataFreshnessService;
        this.universeIngestionService = universeIngestionService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.navRefreshScheduler = navRefreshScheduler;
    }

    /**
     * Retrieves status of the scheduled daily NAV refresh job (Phase A2).
     *
     * GET /api/v1/ingestion/amfi/scheduler
     */
    @GetMapping("/scheduler")
    public ResponseEntity<?> getSchedulerStatus() {
        if (navRefreshScheduler == null) {
            return ResponseEntity.ok(Map.of(
                "schedulerConfigured", false,
                "message", "AmfiNavRefreshScheduler is not active in this profile"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "schedulerConfigured", true,
            "enabled", navRefreshScheduler.isRefreshEnabled(),
            "lastRunTime", navRefreshScheduler.getLastRunTime() != null ? navRefreshScheduler.getLastRunTime().toString() : "N/A",
            "lastRunStatus", navRefreshScheduler.getLastRunStatus(),
            "lastObservationsIngested", navRefreshScheduler.getLastObservationsIngested(),
            "lastDuplicatesSkipped", navRefreshScheduler.getLastDuplicatesSkipped()
        ));
    }

    /**
     * Executes daily AMFI NAV refresh workflow.
     *
     * POST /api/v1/ingestion/amfi/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AmfiNavRefreshResultDto> refreshNav() {
        AmfiNavRefreshResultDto result = navRefreshService.refreshLatestNav();
        if ("FAILED".equals(result.status())) {
            return ResponseEntity.status(502).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves current NAV data freshness read model.
     *
     * GET /api/v1/ingestion/amfi/freshness
     */
    @GetMapping("/freshness")
    public ResponseEntity<AmfiDataFreshnessDto> getFreshness() {
        return ResponseEntity.ok(dataFreshnessService.getFreshnessStatus());
    }

    /**
     * Retrieves machine-readable mutual fund universe & NAV coverage report.
     *
     * GET /api/v1/ingestion/amfi/coverage
     */
    @GetMapping("/coverage")
    public ResponseEntity<UniverseCoverageReport> getUniverseCoverage() {
        Map<String, Object> sampleNavCoverage = historicalNavIngestionService.ingestControlledSample();
        return ResponseEntity.ok(universeIngestionService.generateUniverseCoverageReport(sampleNavCoverage));
    }

    /**
     * Executes controlled historical NAV backfill for the canonical sample cohort.
     *
     * POST /api/v1/ingestion/amfi/backfill
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> executeControlledBackfill() {
        Map<String, Object> result = historicalNavIngestionService.ingestControlledSample();
        return ResponseEntity.ok(result);
    }

    /**
     * Previews a deterministic production historical NAV backfill cohort (dry-run mode).
     *
     * GET /api/v1/ingestion/amfi/backfill/preview
     */
    @GetMapping("/backfill/preview")
    public ResponseEntity<HistoricalBackfillPreviewDto> previewBackfillCohort(
        @RequestParam(defaultValue = "50") int batchSize,
        @RequestParam(defaultValue = "ALL") String tierFilter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.of(2023, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(historicalNavIngestionService.previewProductionCohort(batchSize, tierFilter, start, end));
    }

    /**
     * Executes production cohort historical NAV backfill using shared universe chunk artifacts.
     *
     * POST /api/v1/ingestion/amfi/backfill/cohort
     */
    @PostMapping("/backfill/cohort")
    public ResponseEntity<HistoricalBackfillRunSummaryDto> executeCohortBackfill(
        @RequestParam(defaultValue = "50") int batchSize,
        @RequestParam(defaultValue = "ALL") String tierFilter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate start = startDate != null ? startDate : LocalDate.of(2023, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(historicalNavIngestionService.executeProductionCohortBackfill(batchSize, tierFilter, start, end));
    }
}
