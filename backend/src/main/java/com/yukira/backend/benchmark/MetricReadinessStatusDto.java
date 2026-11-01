package com.yukira.backend.benchmark;

import java.util.List;

/**
 * Metric-specific readiness assessment determining whether prerequisite conditions
 * (fund history sufficiency, benchmark assignment, TRI compatibility, aligned dates,
 * down-market coverage, and risk-free series availability) are satisfied.
 *
 * Epistemic Guardrail:
 *   Readiness != Validation != Approval.
 *   Readiness only confirms that deterministic calculation prerequisites are present.
 */
public record MetricReadinessStatusDto(
    String metricCode,
    String displayName,
    String category,
    boolean benchmarkDependent,
    boolean riskFreeDependent,
    int requiredTradingDays,
    Integer minimalRegressionDays,
    int actualAlignedTradingDays,
    @com.fasterxml.jackson.annotation.JsonProperty("isReady") boolean isReady,
    String readinessState,
    List<String> disqualificationReasons,
    String methodologyStatus
) {
    public static MetricReadinessStatusDto ready(
        String metricCode,
        String displayName,
        String category,
        boolean benchmarkDependent,
        boolean riskFreeDependent,
        int requiredTradingDays,
        Integer minimalRegressionDays,
        int actualDays,
        String methodologyStatus
    ) {
        return new MetricReadinessStatusDto(
            metricCode,
            displayName,
            category,
            benchmarkDependent,
            riskFreeDependent,
            requiredTradingDays,
            minimalRegressionDays,
            actualDays,
            true,
            "READY",
            List.of(),
            methodologyStatus
        );
    }

    public static MetricReadinessStatusDto candidateOnly(
        String metricCode,
        String displayName,
        String category,
        boolean benchmarkDependent,
        boolean riskFreeDependent,
        int requiredTradingDays,
        Integer minimalRegressionDays,
        int actualDays,
        String candidateNotice,
        String methodologyStatus
    ) {
        return new MetricReadinessStatusDto(
            metricCode,
            displayName,
            category,
            benchmarkDependent,
            riskFreeDependent,
            requiredTradingDays,
            minimalRegressionDays,
            actualDays,
            true,
            "READY_CANDIDATE_ONLY",
            List.of(candidateNotice),
            methodologyStatus
        );
    }

    public static MetricReadinessStatusDto disqualified(
        String metricCode,
        String displayName,
        String category,
        boolean benchmarkDependent,
        boolean riskFreeDependent,
        int requiredTradingDays,
        Integer minimalRegressionDays,
        int actualDays,
        List<String> disqualificationReasons,
        String methodologyStatus
    ) {
        return new MetricReadinessStatusDto(
            metricCode,
            displayName,
            category,
            benchmarkDependent,
            riskFreeDependent,
            requiredTradingDays,
            minimalRegressionDays,
            actualDays,
            false,
            "DISQUALIFIED",
            disqualificationReasons != null ? disqualificationReasons : List.of(),
            methodologyStatus
        );
    }
}
