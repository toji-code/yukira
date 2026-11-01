package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.Collections;
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
    PortfolioRiskEvidenceSummaryDto portfolioRiskEvidence,
    Integer distinctSchemeOptionsCount,
    String valuationCoverageMessage,
    List<PortfolioInvestigationItemDto> structuredInvestigations,
    int currentValuedHoldingsCount,
    int staleValuedHoldingsCount,
    int unavailableHoldingsCount,
    BigDecimal totalCurrentMarketValue,
    BigDecimal totalLastKnownValue,
    String valuationFreshnessState,
    String valuationFreshnessMessage,
    String valuationAsOfDate
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
        PortfolioAnalyticalScoreDto portfolioAnalyticalScore,
        PortfolioRiskEvidenceSummaryDto portfolioRiskEvidence,
        Integer distinctSchemeOptionsCount,
        String valuationCoverageMessage,
        List<PortfolioInvestigationItemDto> structuredInvestigations
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, portfolioAnalyticalScore,
            portfolioRiskEvidence, distinctSchemeOptionsCount, valuationCoverageMessage, structuredInvestigations,
            0, valuedHoldingsCount, totalHoldings - valuedHoldingsCount,
            null, totalAvailableValue,
            valuedHoldingsCount > 0 ? "PORTFOLIO_STALE" : (totalHoldings > 0 ? "PORTFOLIO_UNAVAILABLE" : "PORTFOLIO_EMPTY"),
            valuedHoldingsCount > 0 ? "Portfolio values reflect historical last-known NAV observations. No observations meet present-day freshness policy." : "No holdings could be valued.",
            null
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
        ConcentrationAnalysisDto concentrationAnalysis,
        PortfolioAnalyticalScoreDto portfolioAnalyticalScore,
        PortfolioRiskEvidenceSummaryDto portfolioRiskEvidence
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, portfolioAnalyticalScore,
            portfolioRiskEvidence, totalHoldings, null, Collections.emptyList()
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
        ConcentrationAnalysisDto concentrationAnalysis,
        PortfolioAnalyticalScoreDto portfolioAnalyticalScore
    ) {
        this(
            totalHoldings, valuedHoldingsCount, valuationCoverageState, totalAvailableValue,
            totalInvestedAmount, totalAbsoluteGainLoss, totalAbsoluteGainLossPercentage,
            gainLossState, categoryAllocations, amcAllocations, scoredHoldingsCount, holdings,
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, portfolioAnalyticalScore, null,
            totalHoldings, null, Collections.emptyList()
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
            dataQualityLimitations, investigationQuestions, concentrationAnalysis, null, null,
            totalHoldings, null, Collections.emptyList()
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
            dataQualityLimitations, investigationQuestions, null, null, null,
            totalHoldings, null, Collections.emptyList()
        );
    }
}
