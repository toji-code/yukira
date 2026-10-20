package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Authoritative response payload for portfolio historical market-regime replay,
 * constituent loss attribution, trajectory tracking, and defensible modeled sensitivity scenarios.
 */
public record PortfolioStressReplayResponseDto(
    String status,
    String episodeId,
    String episodeTitle,
    LocalDate startDate,
    LocalDate endDate,
    int alignedTradingDaysCount,
    BigDecimal portfolioCumulativeReturnPct,
    BigDecimal benchmarkCumulativeReturnPct,
    BigDecimal activeReturnPct,
    BigDecimal portfolioAnnualizedVolatilityPct,
    BigDecimal benchmarkAnnualizedVolatilityPct,
    BigDecimal portfolioDownsideDeviationPct,
    BigDecimal portfolioMaxDrawdownPct,
    BigDecimal benchmarkMaxDrawdownPct,
    LocalDate peakDate,
    LocalDate troughDate,
    LocalDate recoveryDate,
    BigDecimal worstDailyReturnPct,
    LocalDate worstDailyReturnDate,
    BigDecimal benchmarkWorstDailyReturnPct,
    List<ConstituentReplayResultDto> constituentResults,
    List<TrajectoryPointDto> trajectory,
    StressExposureContextDto exposureContext,
    List<ModeledSensitivityScenarioDto> modeledScenarios,
    Map<String, String> epistemicClassifications,
    List<String> limitations,
    String rebalancingConvention,
    String attributionConvention,
    BigDecimal standaloneSumReturnPct,
    BigDecimal compoundingResidualPct,
    BigDecimal attributionResidualPct,
    String benchmarkCode,
    String benchmarkName,
    Long benchmarkId
) {}

