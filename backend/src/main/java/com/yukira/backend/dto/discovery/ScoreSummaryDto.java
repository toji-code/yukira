package com.yukira.backend.dto.discovery;

import java.math.BigDecimal;

/**
 * Summary of existing YUKIRA Analytical Quality Score (YUKIRA_SCORE_V1) for a scheme option.
 */
public record ScoreSummaryDto(
    boolean available,
    BigDecimal scoreValue,
    String confidence,
    String status,
    String scoreVersion,
    String asOfDate
) {}
