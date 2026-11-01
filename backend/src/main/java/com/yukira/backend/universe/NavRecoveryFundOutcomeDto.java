package com.yukira.backend.universe;

import java.util.List;

/**
 * Result of historical NAV recovery execution for an individual scheme option (Phase A1).
 */
public record NavRecoveryFundOutcomeDto(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    EquityUniverseCategory category,
    String outcomeStatus, // RECOVERED_SUFFICIENT, PARTIAL_PROGRESS, ALREADY_SUFFICIENT, INCEPTION_LIMITED, NO_DATA_AVAILABLE, FAILED
    long previous3YObservations,
    long current3YObservations,
    int observationsInserted,
    int duplicatesSkipped,
    List<Long> sourceArtifactIds,
    Long calculationRunId,
    String calculationStatus,
    String message
) {}
