package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;

/**
 * Delta comparison between two scenarios for an exposure dimension.
 */
public record ScenarioExposureDeltaDto(
    String dimensionType,
    String exposureName,
    BigDecimal portfolioWeightPct,
    BigDecimal shockA,
    BigDecimal shockB,
    BigDecimal shockDelta, // B - A
    BigDecimal contributionA,
    BigDecimal contributionB,
    BigDecimal contributionDelta // B - A
) {}
