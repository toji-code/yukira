package com.yukira.backend.universe;

import java.time.LocalDate;
import java.util.List;

/**
 * Request parameter object for executing bounded stored-artifact historical NAV recovery (Phase A1).
 */
public record StoredArtifactRecoveryRequest(
    Integer batchSize,
    EquityUniverseCategory category,
    List<Long> candidateOptionIds,
    Boolean recalculateProfiles,
    LocalDate asOfDate,
    String operator
) {
    public static StoredArtifactRecoveryRequest ofDefault() {
        return new StoredArtifactRecoveryRequest(20, null, null, true, LocalDate.of(2026, 10, 1), "system-operator");
    }

    public static StoredArtifactRecoveryRequest ofAllCandidates() {
        return new StoredArtifactRecoveryRequest(100, null, null, true, LocalDate.of(2026, 10, 1), "system-operator");
    }

    public int effectiveBatchSize() {
        return (batchSize != null && batchSize > 0) ? Math.min(batchSize, 200) : 20;
    }

    public LocalDate effectiveAsOfDate() {
        return asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);
    }

    public boolean shouldRecalculate() {
        return recalculateProfiles != null ? recalculateProfiles : true;
    }
}
