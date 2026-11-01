package com.yukira.backend.dto.metric;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Structured metric availability evaluation response DTO (Task 131).
 * Formalizes explicit data dependency reporting across all registered metrics,
 * preventing ad-hoc nulls and ungrounded UI failures.
 */
public record MetricAvailabilityEvaluationDto(
    String metricCode,
    String canonicalName,
    String availabilityStatus, // AVAILABLE, PARTIALLY_AVAILABLE, INSUFFICIENT_OBSERVATIONS, MISSING_SOURCE_DATA, STALE_SOURCE_DATA, INCOMPATIBLE_BENCHMARK, METHODOLOGY_UNAVAILABLE, GOVERNANCE_RESTRICTED
    String reasonMessage,
    String requiredHorizon,
    Integer availableObservationsCount,
    Integer requiredObservationsCount,
    LocalDate earliestObservationDate,
    LocalDate latestObservationDate,
    Boolean benchmarkAvailable,
    Boolean riskFreeAvailable,
    String governanceStatus,
    String investigationGuidance
) {

    public record SchemeMetricAvailabilityReport(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        List<MetricAvailabilityEvaluationDto> evaluations,
        int availableMetricsCount,
        int restrictedMetricsCount
    ) {}
}
