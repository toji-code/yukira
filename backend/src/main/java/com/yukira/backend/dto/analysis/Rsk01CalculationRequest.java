package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RSK-01 (3-Year Annualized Volatility) vertical slice.
 * Requires endDate and explicit knowledgeCutoffTime; startDate is strictly computed as endDate minus 36 months (3 years).
 */
public record Rsk01CalculationRequest(
    Long schemeOptionId,
    LocalDate endDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag
) {}
