package com.yukira.backend.dto.portfolio;

import com.yukira.backend.dto.discovery.AnalyticalScoreStateDto;
import com.yukira.backend.scoring.dto.YukiraScoreSummary;

import java.math.BigDecimal;

public record PortfolioHoldingDto(
    Long id,
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String fundName,
    String amcName,
    String category,
    String subcategory,
    String planType,
    String optionType,
    BigDecimal units,
    BigDecimal costBasisAmount,
    BigDecimal investedAmount,
    BigDecimal navValue,
    String navAsOfDate,
    String valuationState,
    BigDecimal availableValue,
    BigDecimal absoluteGainLoss,
    BigDecimal absoluteGainLossPercentage,
    AnalyticalScoreStateDto analyticalScore,
    String dataQualityState,
    String createdAt,
    YukiraScoreSummary yukiraScore,
    BigDecimal portfolioWeight,
    HoldingRiskEvidenceDto riskEvidence,
    String reasonSaved,
    String investigationQuestion,
    String nextStep,
    String valuationFreshnessStatus,
    String freshnessExplanation,
    BigDecimal lastKnownValue,
    BigDecimal currentMarketValue,
    String navAvailabilityTime,
    String navIngestionTime,
    String sourceArtifactHash,
    Integer revisionSeq,
    String qualityAssessment,
    Boolean isSavedInWatchlist,
    Long savedWatchlistId,
    String holdingCompletenessStatus,
    java.util.List<String> missingAttributes
) {
    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt,
        YukiraScoreSummary yukiraScore,
        BigDecimal portfolioWeight,
        HoldingRiskEvidenceDto riskEvidence,
        String reasonSaved,
        String investigationQuestion,
        String nextStep,
        String valuationFreshnessStatus,
        String freshnessExplanation,
        BigDecimal lastKnownValue,
        BigDecimal currentMarketValue,
        String navAvailabilityTime,
        String navIngestionTime,
        String sourceArtifactHash,
        Integer revisionSeq,
        String qualityAssessment
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            portfolioWeight, riskEvidence, reasonSaved, investigationQuestion, nextStep,
            valuationFreshnessStatus, freshnessExplanation, lastKnownValue, currentMarketValue,
            navAvailabilityTime, navIngestionTime, sourceArtifactHash, revisionSeq, qualityAssessment,
            (reasonSaved != null || investigationQuestion != null || nextStep != null),
            null,
            (costBasisAmount != null && "VALUATION_CURRENT".equals(valuationFreshnessStatus)) ? "COMPLETE" : "PARTIAL",
            java.util.Collections.emptyList()
        );
    }
    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt,
        YukiraScoreSummary yukiraScore,
        BigDecimal portfolioWeight,
        HoldingRiskEvidenceDto riskEvidence,
        String reasonSaved,
        String investigationQuestion,
        String nextStep
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            portfolioWeight, riskEvidence, reasonSaved, investigationQuestion, nextStep,
            "VALUATION_AVAILABLE".equals(valuationState) ? "VALUATION_STALE" : "VALUATION_UNAVAILABLE",
            "VALUATION_AVAILABLE".equals(valuationState) ? "Valuation calculated using historical observation (lag exceeds freshness threshold)." : "NAV observation unavailable in database.",
            availableValue, null, null, null, null, 1, "VALID"
        );
    }
    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt,
        YukiraScoreSummary yukiraScore,
        BigDecimal portfolioWeight,
        HoldingRiskEvidenceDto riskEvidence
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            portfolioWeight, riskEvidence, null, null, null
        );
    }

    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt,
        YukiraScoreSummary yukiraScore,
        BigDecimal portfolioWeight
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            portfolioWeight, null, null, null, null
        );
    }

    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt,
        YukiraScoreSummary yukiraScore
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            null, null, null, null, null
        );
    }

    public PortfolioHoldingDto(
        Long id,
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        AnalyticalScoreStateDto analyticalScore,
        String dataQualityState,
        String createdAt
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, null, null, null,
            null, null, null
        );
    }
}
