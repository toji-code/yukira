package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RET-02 Simple Period Return vertical slice.
 * Financial values cannot be injected by callers; identity and parameters must be explicit.
 */
public record Ret02CalculationRequest(
    Long schemeOptionId,
    LocalDate startDate,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
