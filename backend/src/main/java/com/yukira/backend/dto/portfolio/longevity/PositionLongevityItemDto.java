package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Observed tenure and persistence metrics for an individual security across eligible portfolio disclosures.
 *
 * Epistemic Guardrail:
 * Describes observed appearance across Point-in-Time disclosures.
 * Never asserts unobserved transaction dates or manager conviction.
 */
public record PositionLongevityItemDto(
    Long securityId,
    String isin,
    String canonicalName,
    LocalDate firstObservedDate,
    LocalDate lastObservedDate,
    int appearanceCount,
    int spanObservationsCount,
    BigDecimal appearanceRatioPct,
    long observedTenureDays,
    long observedTenureMonths,
    String observedTenureBucket,
    String presenceStatus,
    String persistenceCategory,
    boolean isTop10Holding,
    Integer latestRank,
    BigDecimal latestReportedWeightPct,
    BigDecimal averageObservedWeightPct,
    BigDecimal minObservedWeightPct,
    BigDecimal maxObservedWeightPct,
    BigDecimal weightRangePct,
    BigDecimal weightStandardDeviationPct,
    int consecutiveObservationsCount,
    List<PositionWeightPointDto> weightTrajectory
) {}
