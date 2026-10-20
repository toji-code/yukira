package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;

/**
 * Delta comparison between two scenarios for a constituent fund.
 */
public record ScenarioConstituentDeltaDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal portfolioWeightPct,
    BigDecimal returnA,
    BigDecimal returnB,
    BigDecimal returnDelta, // B - A
    BigDecimal contributionA,
    BigDecimal contributionB,
    BigDecimal contributionDelta // B - A
) {}
