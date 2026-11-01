package com.yukira.backend.riskfree;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Operational request contract for bounded historical FBIL risk-free-rate recovery (Phase A5).
 */
public record RiskFreeHorizonRecoveryRequest(
    @JsonProperty("horizon") String horizon, // "3Y", "5Y", "10Y"
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("maxChunks") Integer maxChunks,
    @JsonProperty("maxChunkDays") Integer maxChunkDays,
    @JsonProperty("forceRefresh") Boolean forceRefresh
) {
    public static RiskFreeHorizonRecoveryRequest of(String horizon, LocalDate referenceDate) {
        return new RiskFreeHorizonRecoveryRequest(horizon, referenceDate, 10, 365, false);
    }
}
