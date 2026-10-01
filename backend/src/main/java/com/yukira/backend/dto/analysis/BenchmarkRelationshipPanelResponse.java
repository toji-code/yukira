package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Investor-facing aggregation DTO for the Fund Profile Benchmark Relationship panel.
 *
 * Values are composed from existing persisted analytical slices only:
 * REL-02/RAT-04 tracking consistency, MKT-01 beta, correlation,
 * and R-Squared.
 */
public record BenchmarkRelationshipPanelResponse(
    PanelContext context,
    PanelMetrics metrics,
    PanelEpistemic epistemic
) {
    public record PanelContext(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String isin,
        String planType,
        String optionType,
        Long benchmarkId,
        String benchmarkName,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoffTime,
        OffsetDateTime evaluatedAt
    ) {}

    public record PanelMetrics(
        MetricValue activeReturn,
        MetricValue trackingError,
        MetricValue informationRatio,
        MetricValue correlation,
        MetricValue beta,
        MetricValue rSquared,
        Integer pairedObservationCount,
        Integer minPairedObservationsRequired,
        Boolean isSufficient,
        String resultState
    ) {}

    public record MetricValue(
        String metricCode,
        String label,
        BigDecimal value,
        String units,
        String status,
        String methodologyStatus,
        String observation,
        String interpretation,
        String limitation
    ) {}

    public record PanelEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkLineage,
        List<CalculationEvidence> calculationEvidence
    ) {}

    public record CalculationEvidence(
        String sourceSlice,
        List<String> metricCodes,
        Long calculationRunId,
        String dataQualityStatus,
        String sourceArtifactSha256,
        String benchmarkLineage
    ) {}
}
