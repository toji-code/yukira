package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record VolatilitySensitivityComparisonDto(
    String comparisonPair, // "1Y_VS_3Y", "3Y_VS_5Y", "1Y_VS_5Y"
    String windowA,
    String windowB,
    BigDecimal volatilityA,
    BigDecimal volatilityB,
    BigDecimal absoluteDifferencePct,
    BigDecimal relativeChangePct,
    String shiftClassification,
    String comparabilityState,
    String explanation
) {}

