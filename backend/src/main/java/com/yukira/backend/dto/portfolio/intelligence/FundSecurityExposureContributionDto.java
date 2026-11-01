package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record FundSecurityExposureContributionDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal fundMarketValue,
    BigDecimal fundWeightInPortfolioPct,
    BigDecimal holdingWeightInFundPct,
    BigDecimal effectiveContributionValue,
    BigDecimal effectiveContributionPct
) {}
