package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record FundSecurityContributionDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal fundWeightInPortfolioPct,
    BigDecimal holdingWeightInFundPct,
    BigDecimal effectiveWeightContributionPct
) {}
