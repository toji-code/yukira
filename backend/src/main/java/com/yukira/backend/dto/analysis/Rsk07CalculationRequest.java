package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RSK-07 (Historical Expected Shortfall / CVaR 95%, 3Y) vertical slice.
 */
public record Rsk07CalculationRequest(
    Long schemeOptionId,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
