package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record DownsideSensitivityComparisonDto(
    BigDecimal maxDrawdown1Y,
    BigDecimal maxDrawdown3Y,
    BigDecimal maxDrawdown5Y,
    BigDecimal var95Pct1Y,
    BigDecimal var95Pct3Y,
    BigDecimal var95Pct5Y,
    BigDecimal expectedShortfall95Pct1Y,
    BigDecimal expectedShortfall95Pct3Y,
    BigDecimal expectedShortfall95Pct5Y,
    int tailObservations1Y,
    int tailObservations3Y,
    int tailObservations5Y,
    String comparabilityState,
    String explanation
) {}

