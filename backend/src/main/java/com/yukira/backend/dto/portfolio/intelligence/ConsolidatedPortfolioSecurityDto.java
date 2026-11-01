package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record ConsolidatedPortfolioSecurityDto(
    String canonicalName,
    String isin,
    String sector,
    String assetClass,
    String marketCapCategory,
    BigDecimal exposureValue,
    BigDecimal portfolioExposurePct,
    BigDecimal disclosedWeightInKnownExposuresPct,
    int fundCount,
    boolean isRepeatedExposure,
    List<FundSecurityExposureContributionDto> contributions,
    String identityConfidence,
    String classificationProvenance
) {
    public String securityId() { return isin != null ? isin : canonicalName; }
    public String securityName() { return canonicalName; }
    public Double portfolioWeightPercentage() { return portfolioExposurePct != null ? portfolioExposurePct.doubleValue() : 0.0; }
    public int fundAppearancesCount() { return fundCount; }
}
