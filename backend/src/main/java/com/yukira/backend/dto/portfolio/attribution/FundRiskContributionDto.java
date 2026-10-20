package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;

public record FundRiskContributionDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String planType,
    BigDecimal allocationWeightPct,
    BigDecimal weightFraction,
    BigDecimal annualizedVolatilityPct,
    BigDecimal marginalContributionPct,
    BigDecimal volatilityContributionPct,
    BigDecimal percentageRiskContribution,
    boolean isNegativeContribution,
    boolean isDiversificationDampener,
    BigDecimal covarianceWithPortfolio,
    String interpretation
) {}
