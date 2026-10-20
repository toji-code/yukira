package com.yukira.backend.controller;

import com.yukira.backend.ingestion.holdings.MonthlyHoldingsRefreshRun;
import com.yukira.backend.ingestion.holdings.MonthlyHoldingsRefreshService;
import com.yukira.backend.ingestion.holdings.TriggerType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for administrative / operational monthly holdings refresh actions.
 *
 * Security & Governance Contract:
 * 1. Endpoint path: /api/v1/admin/holdings/refresh
 * 2. Protection: Requires OAuth2 authentication via SecurityConfig matchers.
 * 3. Scope Scoping: Executes MonthlyHoldingsRefreshService with TriggerType.MANUAL.
 * 4. Safety: Does NOT accept arbitrary URLs; uses registered MonthlyHoldingsSourceRegistry descriptors only.
 */
@RestController
@RequestMapping("/api/v1/admin/holdings")
public class MonthlyHoldingsAdminController {

    private final MonthlyHoldingsRefreshService refreshService;

    public MonthlyHoldingsAdminController(MonthlyHoldingsRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    /**
     * Triggers an immediate manual monthly holdings refresh run across registered mutual funds.
     *
     * POST /api/v1/admin/holdings/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<MonthlyHoldingsRefreshRun> triggerRefresh() {
        MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
        if ("CONCURRENT_RUN_SKIPPED".equalsIgnoreCase(run.overallStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(run);
        }
        return ResponseEntity.ok(run);
    }

    /**
     * Retrieves the operational status, execution history, and pilot freshness of the monthly holdings refresh workflow.
     *
     * GET /api/v1/admin/holdings/status
     */
    @org.springframework.web.bind.annotation.GetMapping("/status")
    public ResponseEntity<com.yukira.backend.ingestion.holdings.MonthlyHoldingsRefreshStatusDto> getOperationalStatus() {
        return ResponseEntity.ok(refreshService.getOperationalStatus());
    }
}
