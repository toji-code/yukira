package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record CorrelationSensitivityComparisonDto(
    Long schemeOptionIdA,
    String fundNameA,
    Long schemeOptionIdB,
    String fundNameB,
    BigDecimal correlation1Y,
    BigDecimal correlation3Y,
    BigDecimal correlation5Y,
    BigDecimal change1Yto3Y,
    BigDecimal change3Yto5Y,
    boolean hasClassificationChanged,
    boolean isMateriallySensitive,
    String comparabilityState,
    String interpretation
) {}

