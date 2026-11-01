package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record FundRiskContributionSensitivityDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    BigDecimal allocationWeightPct,
    BigDecimal percentageRiskContribution1Y,
    BigDecimal percentageRiskContribution3Y,
    BigDecimal percentageRiskContribution5Y,
    BigDecimal change1Yto3Y,
    BigDecimal change3Yto5Y,
    boolean hasSignFlipped,
    boolean hasClassificationShifted,
    String classification1Y,
    String classification3Y,
    String classification5Y,
    String sensitivityNote
) {}

