package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historical weight observation for an individual security at a specific portfolio date.
 */
public record PositionWeightPointDto(
    LocalDate portfolioDate,
    BigDecimal reportedWeightPct,
    Integer holdingRank,
    Boolean isPresent,
    String disclosureScope
) {}
