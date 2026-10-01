package com.yukira.backend.dto.analysis;

import java.time.OffsetDateTime;
import java.util.List;

public record ComparisonResponse(
    List<ComparisonFund> funds,
    List<ComparisonMetric> metrics,
    ComparisonPeriod period,
    Long runId,
    OffsetDateTime executionTimestamp
) {
    public record ComparisonFund(
        Long schemeOptionId,
        String schemeName,
        String schemeCode,
        String amcName,
        String amfiCode,
        String isin,
        String planType,
        String optionType
    ) {}

    public record ComparisonMetricResult(
        Long schemeOptionId,
        Double numericValue,
        String formattedValue,
        String units,
        String calculationStatus,
        String errorMessage,
        Integer observationCount
    ) {}

    public record ComparisonMetric(
        String metricCode,
        String metricName,
        String category,
        String governanceStatus,
        String period,
        String description,
        String interpretation,
        String limitations,
        List<ComparisonMetricResult> results
    ) {}

    public record ComparisonPeriod(
        String startDate,
        String endDate,
        String knowledgeCutoffTime
    ) {}
}
