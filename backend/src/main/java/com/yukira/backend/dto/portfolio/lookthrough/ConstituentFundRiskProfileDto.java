package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

/**
 * Historical risk profile for a constituent fund over the synchronized observation window.
 */
public record ConstituentFundRiskProfileDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    BigDecimal allocationWeightPct,
    BigDecimal annualizedVolatilityPct,
    BigDecimal downsideDeviationPct,
    BigDecimal maxDrawdownPct,
    BigDecimal betaToBenchmark,
    int returnObservationsCount,
    String riskCoverageState
) {}
