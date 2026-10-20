package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;

/**
 * Historical return and loss contribution for an individual constituent fund during the replay period.
 *
 * Epistemic & Methodological Integrity:
 * - cumulativeReturnPct: Standalone constituent cumulative compounded return (R_{j,cum}).
 * - contributionPct: Exact multi-period Carino logarithmic linked portfolio contribution (C_j),
 *   which reconciles down to machine precision with total portfolio cumulative return (sum(C_j) == R_{port}).
 * - standaloneContributionPct: Linear buy-and-hold approximation (a_j * R_{j,cum}). The difference
 *   between portfolio return and sum(a_j * R_{j,cum}) is the Compounding & Daily Rebalancing Interaction Residual.
 */
public record ConstituentReplayResultDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    BigDecimal allocationWeightPct,
    BigDecimal cumulativeReturnPct,
    BigDecimal contributionPct,
    BigDecimal standaloneContributionPct,
    BigDecimal annualizedVolatilityPct,
    BigDecimal maxDrawdownPct,
    BigDecimal worstDailyReturnPct,
    int observationCount
) {}
