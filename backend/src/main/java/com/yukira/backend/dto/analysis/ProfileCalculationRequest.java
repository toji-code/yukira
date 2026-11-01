package com.yukira.backend.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yukira.backend.universe.AnalyticalHorizon;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO to trigger or retrieve a unified Institutional Profile calculation.
 * Supports explicit analytical horizons: 3Y (frozen Phase 2H), 5Y (Phase B1 candidate), 10Y (Phase B1 candidate).
 */
public record ProfileCalculationRequest(
    @JsonProperty("schemeOptionId") Long schemeOptionId,
    @JsonProperty("benchmarkId") Long benchmarkId,
    @JsonProperty("asOfDate") LocalDate asOfDate,
    @JsonProperty("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime,
    @JsonProperty("methodologyTag") String methodologyTag,
    @JsonProperty("metricCodes") List<String> metricCodes,
    @JsonProperty("parameters") Map<String, Object> parameters,
    @JsonProperty("runId") Long runId,
    @JsonProperty("forceRecalculate") Boolean forceRecalculate,
    @JsonProperty("horizon") String horizon
) {
    public ProfileCalculationRequest(
        Long schemeOptionId,
        Long benchmarkId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag,
        List<String> metricCodes,
        Map<String, Object> parameters
    ) {
        this(schemeOptionId, benchmarkId, asOfDate, knowledgeCutoffTime, methodologyTag, metricCodes, parameters, null, false, "3Y");
    }

    public ProfileCalculationRequest(
        Long schemeOptionId,
        Long benchmarkId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        String methodologyTag,
        List<String> metricCodes,
        Map<String, Object> parameters,
        Long runId,
        Boolean forceRecalculate
    ) {
        this(schemeOptionId, benchmarkId, asOfDate, knowledgeCutoffTime, methodologyTag, metricCodes, parameters, runId, forceRecalculate, "3Y");
    }

    /**
     * Resolves the canonical AnalyticalHorizon enum, defaulting to THREE_YEAR (3Y).
     */
    public AnalyticalHorizon effectiveHorizon() {
        if (horizon != null && !horizon.isBlank()) {
            return AnalyticalHorizon.fromCode(horizon);
        }
        if (parameters != null && parameters.containsKey("horizon")) {
            return AnalyticalHorizon.fromCode(parameters.get("horizon").toString());
        }
        return AnalyticalHorizon.THREE_YEAR;
    }
}
