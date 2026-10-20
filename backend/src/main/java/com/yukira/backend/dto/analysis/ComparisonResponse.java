package com.yukira.backend.dto.analysis;

import com.yukira.backend.scoring.dto.YukiraScoreSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public record ComparisonResponse(
    List<ComparisonFund> funds,
    List<ComparisonMetric> metrics,
    ComparisonPeriod period,
    Long runId,
    OffsetDateTime executionTimestamp,
    ComparisonCoverageSummary coverageSummary,
    List<String> investigationPrompts
) {
    public ComparisonResponse(
        List<ComparisonFund> funds,
        List<ComparisonMetric> metrics,
        ComparisonPeriod period,
        Long runId,
        OffsetDateTime executionTimestamp
    ) {
        this(funds, metrics, period, runId, executionTimestamp, null, Collections.emptyList());
    }

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
        Integer observationCount,
        String comparabilityStatus,
        String limitationReason,
        String startDate,
        String endDate,
        String dataDate
    ) {
        public ComparisonMetricResult(
            Long schemeOptionId,
            Double numericValue,
            String formattedValue,
            String units,
            String calculationStatus,
            String errorMessage,
            Integer observationCount,
            String comparabilityStatus,
            String limitationReason
        ) {
            this(
                schemeOptionId,
                numericValue,
                formattedValue,
                units,
                calculationStatus,
                errorMessage,
                observationCount,
                comparabilityStatus,
                limitationReason,
                null,
                null,
                null
            );
        }

        public ComparisonMetricResult(
            Long schemeOptionId,
            Double numericValue,
            String formattedValue,
            String units,
            String calculationStatus,
            String errorMessage,
            Integer observationCount
        ) {
            this(
                schemeOptionId,
                numericValue,
                formattedValue,
                units,
                calculationStatus,
                errorMessage,
                observationCount,
                "CALCULATED".equalsIgnoreCase(calculationStatus) ? "COMPARABLE" : "INSUFFICIENT_DATA",
                "INSUFFICIENT_DATA".equalsIgnoreCase(calculationStatus) ? "Insufficient historical observations available for calculation." : null,
                null,
                null,
                null
            );
        }
    }

    public record ComparisonMetric(
        String metricCode,
        String metricName,
        String category,
        String governanceStatus,
        String period,
        String description,
        String interpretation,
        String limitations,
        String overallComparabilityStatus,
        String overallComparabilityReason,
        List<ComparisonMetricResult> results
    ) {
        public ComparisonMetric(
            String metricCode,
            String metricName,
            String category,
            String governanceStatus,
            String period,
            String description,
            String interpretation,
            String limitations,
            List<ComparisonMetricResult> results
        ) {
            this(
                metricCode,
                metricName,
                category,
                governanceStatus,
                period,
                description,
                interpretation,
                limitations,
                "COMPARABLE",
                "Metric values evaluated under consistent methodology.",
                results
            );
        }
    }

    public record ComparisonPeriod(
        String startDate,
        String endDate,
        String knowledgeCutoffTime
    ) {}

    public record ComparisonCoverageSummary(
        int totalSelectedFunds,
        int scoredFundsCount,
        int completeRiskEvidenceCount,
        int missingMetricsCount,
        boolean differingAsOfDates
    ) {}
}
