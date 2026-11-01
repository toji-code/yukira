package com.yukira.backend.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yukira.backend.universe.AnalyticalHorizon;

import java.util.List;

/**
 * Metric-Level Horizon Eligibility DTO (Phase B1).
 *
 * Epistemic & Quantitative Rules:
 * 1. Horizon support is determined strictly per metric, not by a single global flag.
 * 2. Governance statuses are distinct:
 *    - IMPLEMENTED: calculation code exists.
 *    - VALIDATED: evidence supports correctness under specified methodology.
 *    - APPROVED: formally authorized for live investor decision support.
 *    - UNSUPPORTED: calculation cannot safely execute for this horizon under authorized methodology.
 *    - RETIRED: methodology no longer authorized.
 * 3. Never infer validation or approval from implementation or historical data coverage alone.
 */
public record MetricHorizonEligibilityDto(
    @JsonProperty("metricCode") String metricCode,
    @JsonProperty("metricName") String metricName,
    @JsonProperty("category") String category,
    @JsonProperty("horizon") AnalyticalHorizon horizon,
    @JsonProperty("horizonCode") String horizonCode,
    @JsonProperty("implementationStatus") String implementationStatus, // "IMPLEMENTED", "NOT_IMPLEMENTED"
    @JsonProperty("governanceStatus") String governanceStatus,         // "APPROVED", "VALIDATED", "CANDIDATE", "UNSUPPORTED", "RETIRED"
    @JsonProperty("methodologyVersion") String methodologyVersion,
    @JsonProperty("supported") boolean supported,
    @JsonProperty("requiredSeries") List<String> requiredSeries,       // e.g. ["FUND_NAV"], ["FUND_NAV", "BENCHMARK_TRI"]
    @JsonProperty("observationAlignment") String observationAlignment, // e.g. "BOUNDARY_PAIR_4D_LOOKBACK", "SYNCHRONOUS_CALENDAR_DATES"
    @JsonProperty("defaultThreshold") int defaultThreshold,            // e.g. 700 for 3Y, 1200 for 5Y, 2400 for 10Y
    @JsonProperty("formulaDisclosure") String formulaDisclosure,
    @JsonProperty("knownLimitations") String knownLimitations,
    @JsonProperty("unsupportedReason") String unsupportedReason
) {
    public static MetricHorizonEligibilityDto supported(
        String metricCode,
        String metricName,
        String category,
        AnalyticalHorizon horizon,
        String governanceStatus,
        String methodologyVersion,
        List<String> requiredSeries,
        String observationAlignment,
        String formulaDisclosure,
        String knownLimitations
    ) {
        return new MetricHorizonEligibilityDto(
            metricCode,
            metricName,
            category,
            horizon,
            horizon.getCode(),
            "IMPLEMENTED",
            governanceStatus,
            methodologyVersion,
            true,
            requiredSeries,
            observationAlignment,
            horizon.getDefaultThreshold(),
            formulaDisclosure,
            knownLimitations,
            null
        );
    }

    public static MetricHorizonEligibilityDto unsupported(
        String metricCode,
        String metricName,
        String category,
        AnalyticalHorizon horizon,
        String methodologyVersion,
        List<String> requiredSeries,
        String observationAlignment,
        String formulaDisclosure,
        String knownLimitations,
        String unsupportedReason
    ) {
        return new MetricHorizonEligibilityDto(
            metricCode,
            metricName,
            category,
            horizon,
            horizon.getCode(),
            "IMPLEMENTED",
            "UNSUPPORTED",
            methodologyVersion,
            false,
            requiredSeries,
            observationAlignment,
            horizon.getDefaultThreshold(),
            formulaDisclosure,
            knownLimitations,
            unsupportedReason
        );
    }
}
