package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioScoreHistoryDto(
    LocalDate requestedAsOfDate,
    String selectionPolicy,
    PortfolioAnalyticalScoreDto currentPortfolioScore,
    HistoricalPortfolioScoreAvailabilityDto historicalPortfolioScore,
    List<HoldingScoreHistoryDto> holdings,
    List<String> dataQualityLimitations,
    List<String> investigationQuestions
) {
    public record HistoricalPortfolioScoreAvailabilityDto(
        String state,
        LocalDate requestedAsOfDate,
        BigDecimal portfolioScore,
        String reason,
        String evidenceBoundary
    ) {}

    public record HoldingScoreHistoryDto(
        Long schemeOptionId,
        String fundName,
        String amcName,
        String amfiCode,
        String isin,
        HoldingPerformanceDto holdingPerformance,
        ScoreSnapshotDto currentFundScore,
        ScoreSnapshotDto selectedHistoricalFundScore,
        List<ScoreSnapshotDto> historicalFundScores
    ) {}

    public record HoldingPerformanceDto(
        String valuationState,
        BigDecimal units,
        BigDecimal availableValue,
        BigDecimal investedAmount,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        BigDecimal currentPortfolioWeight
    ) {}

    public record ScoreSnapshotDto(
        String availabilityState,
        Long analyticalScoreId,
        Long schemeOptionId,
        BigDecimal score,
        BigDecimal confidence,
        String status,
        String scoreVersion,
        String methodologyStatus,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        Long calculationRunId,
        String referencePopulation,
        String unavailableReason,
        List<DimensionSnapshotDto> dimensions
    ) {}

    public record DimensionSnapshotDto(
        Long id,
        String dimension,
        String dimensionName,
        BigDecimal score,
        BigDecimal weight,
        String status,
        BigDecimal confidence,
        Integer eligibleMetricCount,
        Integer totalMetricCount,
        List<MetricContributionSnapshotDto> metricContributions
    ) {}

    public record MetricContributionSnapshotDto(
        Long id,
        String metricCode,
        String metricName,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        String direction,
        BigDecimal weight,
        BigDecimal contribution,
        String eligibility,
        String exclusionReason,
        String unit
    ) {}
}