package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Structured DTO representing an excluded holding and the backend reason for its exclusion from Portfolio Analytical Score V1.
 */
public record ExcludedHoldingScoreDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal portfolioWeight,
    String exclusionReason
) {}
