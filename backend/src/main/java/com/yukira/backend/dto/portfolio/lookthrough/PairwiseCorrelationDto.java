package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

/**
 * Historical return correlation between two constituent funds over the synchronized window.
 */
public record PairwiseCorrelationDto(
    Long fundAId,
    String fundAName,
    Long fundBId,
    String fundBName,
    BigDecimal correlationCoefficient,
    int commonTradingDays
) {}
