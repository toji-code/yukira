package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Request parameter object for executing bounded multi-horizon historical NAV recovery (Phase A3).
 */
public record HorizonRecoveryRequest(
    @JsonProperty("horizon") AnalyticalHorizon horizon,
    @JsonProperty("asOfDate") LocalDate asOfDate,
    @JsonProperty("batchSize") Integer batchSize,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("candidateOptionIds") List<Long> candidateOptionIds,
    @JsonProperty("triggerCalculation") Boolean triggerCalculation,
    @JsonProperty("maxIntervalsPerFund") Integer maxIntervalsPerFund,
    @JsonProperty("operator") String operator
) {
    public static HorizonRecoveryRequest ofDefault(AnalyticalHorizon horizon) {
        return new HorizonRecoveryRequest(
            horizon != null ? horizon : AnalyticalHorizon.FIVE_YEAR,
            LocalDate.of(2026, 10, 1),
            10,
            null,
            null,
            true,
            10,
            "system-operator"
        );
    }

    public AnalyticalHorizon effectiveHorizon() {
        return horizon != null ? horizon : AnalyticalHorizon.FIVE_YEAR;
    }

    public LocalDate effectiveAsOfDate() {
        return asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);
    }

    public int effectiveBatchSize() {
        return (batchSize != null && batchSize > 0) ? Math.min(batchSize, 50) : 10;
    }

    public boolean shouldTriggerCalculation() {
        return triggerCalculation != null ? triggerCalculation : true;
    }

    public int effectiveMaxIntervals() {
        return (maxIntervalsPerFund != null && maxIntervalsPerFund > 0) ? maxIntervalsPerFund : 10;
    }
}
