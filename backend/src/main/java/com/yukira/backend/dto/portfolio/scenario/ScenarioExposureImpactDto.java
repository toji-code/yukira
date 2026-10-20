package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;

/**
 * Breakdown of scenario estimated impact by exposure dimension (Market Cap bucket, Sector, or Broad Market).
 */
public record ScenarioExposureImpactDto(
    String dimensionType, // BROAD_MARKET, MARKET_CAP, SECTOR, CASH, UNMODELED
    String exposureName,
    BigDecimal portfolioWeightPct,
    BigDecimal assumedShockPct,
    BigDecimal estimatedContributionPct,
    String coverageStatus // COVERED, UNMODELED, PARTIALLY_MODELED
) {}
