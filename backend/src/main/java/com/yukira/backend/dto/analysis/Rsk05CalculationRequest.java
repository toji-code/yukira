package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RSK-05 (Ulcer Index, 3Y) vertical slice.
 */
public record Rsk05CalculationRequest(
    Long schemeOptionId,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
