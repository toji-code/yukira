package com.yukira.backend.universe;

import java.time.LocalDate;
import java.util.List;

/**
 * Request parameter object for executing bounded targeted historical interval recovery (Phase A1).
 */
public record TargetedIntervalRecoveryRequest(
    Integer batchSize,
    EquityUniverseCategory category,
    List<Long> candidateOptionIds,
    Boolean recalculateProfiles,
    LocalDate asOfDate,
    Integer maxIntervalsPerFund,
    String operator
) {
    public static TargetedIntervalRecoveryRequest ofDefault() {
        return new TargetedIntervalRecoveryRequest(10, null, null, true, LocalDate.of(2026, 10, 1), 10, "system-operator");
    }

    public static TargetedIntervalRecoveryRequest ofAllCandidates() {
        return new TargetedIntervalRecoveryRequest(50, null, null, true, LocalDate.of(2026, 10, 1), 10, "system-operator");
    }

    public int effectiveBatchSize() {
        return (batchSize != null && batchSize > 0) ? Math.min(batchSize, 100) : 10;
    }

    public LocalDate effectiveAsOfDate() {
        return asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);
    }

    public boolean shouldRecalculate() {
        return recalculateProfiles != null ? recalculateProfiles : true;
    }

    public int effectiveMaxIntervals() {
        return (maxIntervalsPerFund != null && maxIntervalsPerFund > 0) ? maxIntervalsPerFund : 10;
    }
}
