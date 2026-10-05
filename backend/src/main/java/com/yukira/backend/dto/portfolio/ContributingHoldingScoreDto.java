package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Structured DTO representing a contributing holding's analytical score input to Portfolio Analytical Score V1.
 */
public record ContributingHoldingScoreDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal fundScore,
    String scoreStatus,
    String scoreAsOfDate,
    BigDecimal portfolioWeight,
    BigDecimal normalizedScoreWeight,
    BigDecimal scoreContribution
) {}
