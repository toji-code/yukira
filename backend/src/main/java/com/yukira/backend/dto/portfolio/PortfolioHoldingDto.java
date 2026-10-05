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
    HoldingRiskEvidenceDto riskEvidence
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
        BigDecimal portfolioWeight
    ) {
        this(
            id, schemeOptionId, amfiCode, isin, fundName, amcName, category, subcategory, planType, optionType,
            units, costBasisAmount, investedAmount, navValue, navAsOfDate, valuationState, availableValue,
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, yukiraScore,
            portfolioWeight, null
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
            null, null
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
            absoluteGainLoss, absoluteGainLossPercentage, analyticalScore, dataQualityState, createdAt, null, null, null
        );
    }
}
