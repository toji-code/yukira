package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryDto(
    int totalHoldings,
    int valuedHoldingsCount,
    String valuationCoverageState,
    BigDecimal totalAvailableValue,
    BigDecimal totalInvestedAmount,
    BigDecimal totalAbsoluteGainLoss,
    BigDecimal totalAbsoluteGainLossPercentage,
    String gainLossState,
    List<CategoryAllocationDto> categoryAllocations,
    List<AmcAllocationDto> amcAllocations,
    int scoredHoldingsCount,
    List<PortfolioHoldingDto> holdings,
    List<String> dataQualityLimitations,
    List<String> investigationQuestions,
    ConcentrationAnalysisDto concentrationAnalysis,
    PortfolioAnalyticalScoreDto portfolioAnalyticalScore,
    PortfolioRiskEvidenceSummaryDto portfolioRiskEvidence
) {
    public PortfolioSummaryDto(
        int totalHoldings,
        int valuedHoldingsCount,
        String valuationCoverageState,
        BigDecimal totalAvailableValue,
        BigDecimal totalInvestedAmount,
        BigDecimal totalAbsoluteGainLoss,
        BigDecimal totalAbsoluteGainLossPercentage,
        String gainLossState,
        List<CategoryAllocationDto> categoryAllocations,
        List<AmcAllocationDto> amcAllocations,
        int scoredHoldingsCount,
        List<PortfolioHoldingDto> holdings,
        List<String> dataQualityLimitations,
        List<String> investigationQuestions,
        ConcentrationAnalysisDto concentrationAnalysis,
        PortfolioAnalyticalScoreDto portfolioAnalyticalScore
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, portfolioAnalyticalScore, null
        );
    }

    public PortfolioSummaryDto(
        int totalHoldings,
        int valuedHoldingsCount,
        String valuationCoverageState,
        BigDecimal totalAvailableValue,
        BigDecimal totalInvestedAmount,
        BigDecimal totalAbsoluteGainLoss,
        BigDecimal totalAbsoluteGainLossPercentage,
        String gainLossState,
        List<CategoryAllocationDto> categoryAllocations,
        List<AmcAllocationDto> amcAllocations,
        int scoredHoldingsCount,
        List<PortfolioHoldingDto> holdings,
        List<String> dataQualityLimitations,
        List<String> investigationQuestions,
        ConcentrationAnalysisDto concentrationAnalysis
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, null, null
        );
    }

    public PortfolioSummaryDto(
        int totalHoldings,
        int valuedHoldingsCount,
        String valuationCoverageState,
        BigDecimal totalAvailableValue,
        BigDecimal totalInvestedAmount,
        BigDecimal totalAbsoluteGainLoss,
        BigDecimal totalAbsoluteGainLossPercentage,
        String gainLossState,
        List<CategoryAllocationDto> categoryAllocations,
        List<AmcAllocationDto> amcAllocations,
        int scoredHoldingsCount,
        List<PortfolioHoldingDto> holdings,
        List<String> dataQualityLimitations,
        List<String> investigationQuestions
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, null, null, null
        );
    }
}
