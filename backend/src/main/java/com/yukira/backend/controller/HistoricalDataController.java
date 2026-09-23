package com.yukira.backend.controller;

import com.yukira.backend.dto.analysis.AnalyticalObservationSeriesDto;
import com.yukira.backend.service.HistoricalAnalyticalDataService;
import com.yukira.backend.service.TradingDateContinuityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * REST API for Historical Analytical Observation Series & Continuity Audits.
 *
 * Enforces strict point-in-time parameters:
 * Rejects any historical query missing an explicit knowledgeCutoff.
 */
@RestController
@RequestMapping("/api/v1/schemes/options")
public class HistoricalDataController {

    private final HistoricalAnalyticalDataService historicalDataService;
    private final TradingDateContinuityService continuityService;

    public HistoricalDataController(
        HistoricalAnalyticalDataService historicalDataService,
        TradingDateContinuityService continuityService
    ) {
        this.historicalDataService = historicalDataService;
        this.continuityService = continuityService;
    }

    @GetMapping("/{id}/historical-series")
    public ResponseEntity<?> getHistoricalSeries(
        @PathVariable("id") Long schemeOptionId,
        @RequestParam(name = "startDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(name = "analysisCutoff", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate analysisCutoff,
        @RequestParam(name = "knowledgeCutoff", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff
    ) {
        if (knowledgeCutoff == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_KNOWLEDGE_CUTOFF",
                "message", "Strict PIT Invariant: knowledgeCutoff parameter is mandatory to prevent look-ahead bias."
            ));
        }

        LocalDate effectiveCutoff = analysisCutoff != null ? analysisCutoff : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveCutoff.minusYears(5);

        if (effectiveCutoff.isBefore(effectiveStart)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "analysisCutoff cannot be before startDate"
            ));
        }

        if (effectiveCutoff.isAfter(knowledgeCutoff.toLocalDate())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "ANALYSIS_CUTOFF_EXCEEDS_KNOWLEDGE_CUTOFF",
                "message", "Strict PIT Invariant: analysisCutoff cannot exceed knowledgeCutoff date."
            ));
        }

        try {
            AnalyticalObservationSeriesDto series = historicalDataService.getHistoricalObservationSeries(
                schemeOptionId, effectiveStart, effectiveCutoff, knowledgeCutoff
            );
            return ResponseEntity.ok(series);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "HISTORICAL_QUERY_FAILED",
                "message", e.getMessage()
            ));
        }
    }
}
