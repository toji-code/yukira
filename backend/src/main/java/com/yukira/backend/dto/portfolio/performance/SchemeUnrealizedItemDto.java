package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SchemeUnrealizedItemDto(
    Long schemeOptionId,
    String schemeName,
    BigDecimal units,
    BigDecimal latestNav,
    LocalDate navEffectiveDate,
    Boolean isNavStale,
    BigDecimal currentMarketValue,
    BigDecimal remainingCostBasis,
    BigDecimal unrealizedGainOrLoss,
    BigDecimal unrealizedPercentage,
    String holdingsReconciliationStatus,
    String valuationQualityState
) {}

