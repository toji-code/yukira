package com.yukira.backend.dto.discovery;

import java.math.BigDecimal;

/**
 * Analytical score state for a scheme option.
 */
public record AnalyticalScoreStateDto(
    boolean available,
    BigDecimal scoreValue,
    String confidence,
    String status,
    String scoreVersion,
    String asOfDate
) {}
