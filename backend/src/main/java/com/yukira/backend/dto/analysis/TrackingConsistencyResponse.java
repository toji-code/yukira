package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable API response DTO for Market-Relative Tracking Consistency & Information Ratio Analysis
 * (REL-02 Tracking Error & RAT-04 Information Ratio).
 */
public record TrackingConsistencyResponse(
    TrackingContext context,
    TrackingMetrics metrics,
    TrackingEpistemic epistemic
) {
    public record TrackingContext(
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

    public record TrackingMetrics(
        BigDecimal trackingErrorAnnualized,
        String trackingErrorStatus,
        BigDecimal meanDailyExcessReturn,
        BigDecimal annualizedMeanActiveReturn,
        BigDecimal informationRatio,
        String informationRatioStatus,
        Integer pairedObservationsCount,
        Integer minPairedObservationsRequired,
        Boolean isSufficientObservations,
        Integer periodsPerYear,
        String annualizationConvention,
        String denominatorConvention,
        Boolean zeroTrackingError
    ) {}

    public record TrackingEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkLineage,
        String sourceArtifactSha256,
        Long calculationRunId
    ) {}
}
