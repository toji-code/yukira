package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;

/**
 * Constituent-level sensitivity breakdown for a modeled stress scenario.
 */
public record ConstituentSensitivityDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal allocationWeightPct,
    BigDecimal betaToBenchmark,
    BigDecimal estimatedLossPct,
    BigDecimal contributionPct
) {}
