package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RSK-06 (Historical VaR 95%, 3Y) vertical slice.
 */
public record Rsk06CalculationRequest(
    Long schemeOptionId,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
