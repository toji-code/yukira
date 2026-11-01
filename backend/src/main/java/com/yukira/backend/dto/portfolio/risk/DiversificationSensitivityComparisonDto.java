package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record DiversificationSensitivityComparisonDto(
    BigDecimal diversificationEffect1Y,
    BigDecimal diversificationEffect3Y,
    BigDecimal diversificationEffect5Y,
    BigDecimal diversificationRatio1Y,
    BigDecimal diversificationRatio3Y,
    BigDecimal diversificationRatio5Y,
    BigDecimal changeEffect1Yto3Y,
    BigDecimal changeEffect3Yto5Y,
    String comparabilityState,
    String explanation
) {}

