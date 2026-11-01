package com.yukira.backend.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yukira.backend.universe.AnalyticalHorizon;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Request DTO for executing RSK-01 (Annualized Volatility) vertical slice.
 * Supports explicit analytical horizons: 3Y (frozen Phase 2H), 5Y (Phase B1 candidate), 10Y (Phase B1 candidate).
 * Requires endDate and explicit knowledgeCutoffTime; startDate is strictly computed as endDate minus horizon years.
 */
public record Rsk01CalculationRequest(
    @JsonProperty("schemeOptionId") Long schemeOptionId,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime,
    @JsonProperty("methodologyTag") String methodologyTag,
    @JsonProperty("horizon") String horizon
) {
    public Rsk01CalculationRequest(
        Long schemeOptionId,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag
    ) {
        this(schemeOptionId, endDate, knowledgeCutoffTime, methodologyTag, "3Y");
    }

    public AnalyticalHorizon effectiveHorizon() {
        return AnalyticalHorizon.fromCode(horizon != null && !horizon.isBlank() ? horizon : "3Y");
    }
}
