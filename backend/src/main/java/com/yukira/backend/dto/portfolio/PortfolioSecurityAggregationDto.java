package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSecurityAggregationDto(
    int totalConstituentFunds,
    int valuedConstituentFunds,
    BigDecimal totalPortfolioValue,
    BigDecimal disclosedCoveragePercentage,
    int uniqueSecuritiesCount,
    List<AggregatedSecurityExposureDto> topSecurityExposures,
    String qualityState,
    List<String> limitations
) {
    public record AggregatedSecurityExposureDto(
        String securityName,
        String isin,
        String sector,
        String assetClass,
        BigDecimal lookThroughWeightPct,
        String formattedLookThroughWeight,
        BigDecimal estimatedMarketValue,
        int holdingFundCount,
        List<ConstituentFundExposureDto> constituentFundExposures
    ) {}

    public record ConstituentFundExposureDto(
        Long schemeOptionId,
        String fundName,
        BigDecimal fundWeightInPortfolioPct,
        BigDecimal holdingWeightInFundPct,
        BigDecimal effectiveLookThroughWeightPct
    ) {}
}
