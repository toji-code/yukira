package com.yukira.backend.dto.analysis;

import com.yukira.backend.scoring.dto.YukiraScoreSummary;
import java.math.BigDecimal;
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
        String optionType,
        YukiraScoreSummary yukiraScore,
        PortfolioHoldingContext portfolioHolding,
        FundEnrichmentSummary enrichment
    ) {
        public ComparisonFund(
            Long schemeOptionId,
            String schemeName,
            String schemeCode,
            String amcName,
            String amfiCode,
            String isin,
            String planType,
            String optionType,
            YukiraScoreSummary yukiraScore
        ) {
            this(schemeOptionId, schemeName, schemeCode, amcName, amfiCode, isin, planType, optionType, yukiraScore, null, null);
        }

        public ComparisonFund(
            Long schemeOptionId,
            String schemeName,
            String schemeCode,
            String amcName,
            String amfiCode,
            String isin,
            String planType,
            String optionType
        ) {
            this(schemeOptionId, schemeName, schemeCode, amcName, amfiCode, isin, planType, optionType, null, null, null);
        }
    }

    public record PortfolioHoldingContext(
        BigDecimal units,
        BigDecimal availableValue,
        BigDecimal costBasis,
        BigDecimal currentWeightPercentage,
        BigDecimal unrealizedGainLossAmount,
        BigDecimal unrealizedGainLossPercentage
    ) {}

    public record FundEnrichmentSummary(
        BigDecimal aumInCrores,
        BigDecimal expenseRatioPct,
        String fundManagerName
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
