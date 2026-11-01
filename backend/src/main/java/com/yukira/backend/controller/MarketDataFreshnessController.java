package com.yukira.backend.controller;

import com.yukira.backend.dto.ingestion.MarketDataFreshnessDto;
import com.yukira.backend.service.ingestion.MarketDataFreshnessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST API exposing aggregated market-data freshness for investor-facing products and operational monitoring (Phase A2).
 *
 * Epistemic Guardrail:
 * Unifies mutual fund NAVs, NSE category benchmark TRIs, and FBIL risk-free yields
 * while decoupling temporal freshness from 3-year historical calculation sufficiency.
 */
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataFreshnessController {

    private final MarketDataFreshnessService marketDataFreshnessService;

    public MarketDataFreshnessController(MarketDataFreshnessService marketDataFreshnessService) {
        this.marketDataFreshnessService = marketDataFreshnessService;
    }

    /**
     * Retrieves unified market-data freshness across NAVs, Category Benchmarks, and Risk-Free Rates.
     *
     * GET /api/v1/market-data/freshness
     */
    @GetMapping("/freshness")
    public ResponseEntity<MarketDataFreshnessDto> getMarketDataFreshness(
        @RequestParam(value = "referenceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        return ResponseEntity.ok(marketDataFreshnessService.getAggregatedFreshness(evalDate));
    }
}
