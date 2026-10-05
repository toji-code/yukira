package com.yukira.backend.scoring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalyticalScoreResponse(
    Long scoreId,
    Long schemeOptionId,
    String schemeName,
    String amfiCode,
    String isin,
    BigDecimal score,
    BigDecimal confidence,
    String status,
    String scoreVersion,
    String methodologyStatus,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    Long calculationRunId,
    String referencePopulation,
    String summary,
    String disclaimer,
    List<DimensionScoreDto> dimensions,
    EvidenceConfidenceDto evidenceConfidence
) {

    public record DimensionScoreDto(
        Long id,
        String dimension,
        String dimensionName,
        BigDecimal score,
        BigDecimal weight,
        BigDecimal effectiveWeight,
        BigDecimal contribution,
        String status,
        BigDecimal confidence,
        int eligibleMetricCount,
        int totalMetricCount,
        List<MetricContributionDto> metricContributions
    ) {}

    public record MetricContributionDto(
        Long id,
        String metricCode,
        String metricName,
        BigDecimal rawValue,
        String formattedRawValue,
        BigDecimal normalizedValue,
        String direction,
        BigDecimal weight,
        BigDecimal effectiveWeight,
        BigDecimal contribution,
        Integer observationCount,
        String eligibility,
        String exclusionReason,
        String unit,
        Long metricResultId
    ) {}

    public record EvidenceConfidenceDto(
        int totalObservations,
        int validObservations,
        int suspiciousObservations,
        int invalidObservations,
        int pairedBenchmarkObservations,
        int pairedReturnPeriods,
        boolean meetsObservationThreshold,
        boolean sourceArtifactVerified,
        boolean pitIntegrityMaintained,
        BigDecimal confidenceScore,
        String assessment,
        String observationNotes
    ) {}
}
