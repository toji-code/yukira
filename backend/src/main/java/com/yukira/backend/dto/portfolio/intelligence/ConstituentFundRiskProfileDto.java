package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record ConstituentFundRiskProfileDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal portfolioWeightPct,
    BigDecimal annualizedVolatilityPct,
    BigDecimal downsideSemideviationPct,
    BigDecimal maxDrawdownPct,
    BigDecimal beta,
    String benchmarkName,
    String riskDataStatus
) {}
