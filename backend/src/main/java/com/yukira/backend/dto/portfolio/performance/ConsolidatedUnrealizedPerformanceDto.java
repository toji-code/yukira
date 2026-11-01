package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consolidated unrealized performance based on verified point-in-time valuation inputs.
 *
 * Epistemic Rules:
 * 1. Exposes missing NAV observations and stale valuations explicitly.
 * 2. Does not assume an unvalued position has zero market value; marks it as valuation-pending.
 * 3. Never uses future NAV observations when reconstructing historical as-of performance.
 */
public record ConsolidatedUnrealizedPerformanceDto(
    BigDecimal totalPortfolioMarketValue,
    BigDecimal totalRemainingCostBasis,
    BigDecimal totalUnrealizedGainOrLoss,
    BigDecimal unrealizedGainPercentage,
    Integer activeOpenLotsCount,
    Integer valuedSchemesCount,
    Integer unvaluedSchemesCount,
    Integer staleValuationSchemesCount,
    List<SchemeUnrealizedItemDto> items,
    String valuationQualityNotes
) {}

