package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Constituent-level impact and exposure breakdown under scenario shock assumptions.
 */
public record ScenarioConstituentImpactDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    BigDecimal portfolioWeightPct,
    BigDecimal fundBeta,
    BigDecimal estimatedFundReturnPct,
    BigDecimal portfolioContributionPct,
    BigDecimal largeCapWeightPct,
    BigDecimal midCapWeightPct,
    BigDecimal smallCapWeightPct,
    BigDecimal cashWeightPct,
    BigDecimal unclassifiedWeightPct,
    Map<String, BigDecimal> sectorWeightsPct
) {}
