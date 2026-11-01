package com.yukira.backend.universe;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Summary of a bounded historical NAV recovery batch run (Phase A1).
 */
public record NavRecoveryBatchExecutionResultDto(
    String runId,
    String recoveryType, // STORED_ARTIFACT_RECOVERY, TARGETED_INTERVAL_RECOVERY
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    int batchSize,
    int candidatesSelected,
    int newlySufficientCount,
    int totalObservationsInserted,
    int totalDuplicatesSkipped,
    long durationMs,
    List<NavRecoveryFundOutcomeDto> fundOutcomes
) {}
