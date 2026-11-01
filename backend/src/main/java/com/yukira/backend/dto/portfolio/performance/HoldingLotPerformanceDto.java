package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Holding-level FIFO purchase lot tracking record.
 *
 * Epistemic Rules:
 * 1. Links open and disposed unit tranches directly to dated acquisition transactions.
 * 2. Holding period category (SHORT_TERM vs LONG_TERM) is a mathematical duration indicator (<365 vs >=365 days)
 *    and does not constitute tax computation or tax advice.
 */
public record HoldingLotPerformanceDto(
    Long lotId,
    Long schemeOptionId,
    String schemeName,
    Long acquisitionTransactionId,
    LocalDate acquisitionDate,
    BigDecimal acquisitionUnits,
    BigDecimal acquisitionNav,
    BigDecimal acquisitionCost,
    BigDecimal remainingUnits,
    BigDecimal remainingCostBasis,
    BigDecimal currentNav,
    BigDecimal currentMarketValue,
    BigDecimal unrealizedGain,
    BigDecimal realizedGainFromDisposedUnits,
    Long holdingPeriodDays,
    String holdingPeriodCategory,
    Boolean isFullyConsumed,
    String lotStatus // ACTIVE, PARTIALLY_REDEEMED, FULLY_REDEEMED
) {}

