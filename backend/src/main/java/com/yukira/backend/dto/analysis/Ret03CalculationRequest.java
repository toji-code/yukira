package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RET-03 (3Y CAGR) vertical slice.
 * Only requires endDate; startDate is strictly computed as endDate minus 3 years.
 */
public record Ret03CalculationRequest(
    Long schemeOptionId,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
