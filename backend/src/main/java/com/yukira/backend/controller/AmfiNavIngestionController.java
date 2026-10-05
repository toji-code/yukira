package com.yukira.backend.controller;

import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import com.yukira.backend.service.ingestion.AmfiDataFreshnessService;
import com.yukira.backend.service.ingestion.AmfiNavRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller for Production AMFI NAV Refresh & Data Freshness operations.
 *
 * Provides controlled, idempotent daily NAV ingestion from official AMFI portal sources
 * and exposes quantitative data freshness indicators.
 *
 * Epistemic constraint: NAV ingestion strictly preserves raw data immutability and provenance.
 * It does NOT execute quant engine calculations or alter historical score snapshots.
 */
@RestController
@RequestMapping("/api/v1/ingestion/amfi")
public class AmfiNavIngestionController {

    private final AmfiNavRefreshService navRefreshService;
    private final AmfiDataFreshnessService dataFreshnessService;

    public AmfiNavIngestionController(
        AmfiNavRefreshService navRefreshService,
        AmfiDataFreshnessService dataFreshnessService
    ) {
        this.navRefreshService = navRefreshService;
        this.dataFreshnessService = dataFreshnessService;
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
}
