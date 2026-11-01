package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Descriptive investor cash-flow timing analysis.
 *
 * Epistemic Rules:
 * 1. Purely descriptive of transaction timing and magnitude over time.
 * 2. Does NOT make causal claims (e.g. "bought at peak") unless verified dated NAV observations support it.
 * 3. Does NOT describe XIRR vs CAGR differences as "alpha" or "timing skill".
 */
public record CashFlowTimingContextDto(
    LocalDate coverageStartDate,
    LocalDate coverageEndDate,
    Integer totalContributionsCount,
    BigDecimal totalLumpSumContributions,
    BigDecimal totalSipContributions,
    Integer totalRedemptionsCount,
    Integer totalInternalTransfersCount,
    List<MonthlyCashFlowAggregateDto> periodicCashFlowTimeline,
    List<String> timingObservations
) {}

