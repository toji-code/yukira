package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record AugmentedSecurityComparisonDto(
    String securityName,
    String isin,
    String sector,
    BigDecimal baselineWeightPct,
    BigDecimal candidateContributionPct,
    BigDecimal simulatedWeightPct,
    BigDecimal deltaWeightPct
) {}
