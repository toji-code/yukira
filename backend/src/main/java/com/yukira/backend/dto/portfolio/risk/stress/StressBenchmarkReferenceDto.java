package com.yukira.backend.dto.portfolio.risk.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Metadata and observation coverage of the broad Indian equity TRI benchmark
 * used as the market-stress identification reference (e.g. NIFTY_500_TRI or fallback) (Task 37).
 *
 * Epistemic Guardrails:
 * - Preserves explicit benchmark identity (NIFTY_500_TRI preferred, NIFTY_50_TRI fallback).
 * - Total Returns Index (TRI) series mandatory; price-index substitutes prohibited.
 * - Exposes fallback reason and observation window bounds for cross-episode comparability.
 */
public record StressBenchmarkReferenceDto(
    String benchmarkCode,
    String benchmarkName,
    String provider,
    Long benchmarkId,
    LocalDate minObservationDate,
    LocalDate maxObservationDate,
    int totalObservationCount,
    String status,
    boolean isFallbackBenchmark,
    String comparabilityNotice,
    String benchmarkSeriesType,
    String fallbackReason,
    String benchmarkMethodologyVersion,
    BigDecimal observationCoverageRatioPct,
    String effectiveObservationWindow
) {
    /**
     * Backward-compatible 10-parameter constructor (Tasks 25-29 compatibility).
     */
    public StressBenchmarkReferenceDto(
        String benchmarkCode,
        String benchmarkName,
        String provider,
        Long benchmarkId,
        LocalDate minObservationDate,
        LocalDate maxObservationDate,
        int totalObservationCount,
        String status,
        boolean isFallbackBenchmark,
        String comparabilityNotice
    ) {
        this(
            benchmarkCode,
            benchmarkName,
            provider,
            benchmarkId,
            minObservationDate,
            maxObservationDate,
            totalObservationCount,
            status,
            isFallbackBenchmark,
            comparabilityNotice,
            "TOTAL_RETURNS_INDEX",
            isFallbackBenchmark ? "Preferred broad-market benchmark NIFTY_500_TRI was unavailable or lacked required observation history at the knowledge cutoff." : null,
            "BENCHMARK_METHODOLOGY_V1",
            BigDecimal.valueOf(100.0),
            minObservationDate != null && maxObservationDate != null ? minObservationDate + " to " + maxObservationDate : "None"
        );
    }

    /**
     * Backward-compatible 8-parameter constructor (Task 23 compatibility).
     */
    public StressBenchmarkReferenceDto(
        String benchmarkCode,
        String benchmarkName,
        String provider,
        Long benchmarkId,
        LocalDate minObservationDate,
        LocalDate maxObservationDate,
        int totalObservationCount,
        String status
    ) {
        this(
            benchmarkCode,
            benchmarkName,
            provider,
            benchmarkId,
            minObservationDate,
            maxObservationDate,
            totalObservationCount,
            status,
            false,
            null
        );
    }
}
