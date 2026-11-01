package com.yukira.backend.universe;

import java.util.List;

/**
 * Audit detail for an individual SchemeOption evaluated during a bounded batch workflow run (TSK-M1-06).
 */
public record SchemeBatchExecutionDetailDto(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    EquityUniverseCategory category,
    String benchmarkCode,
    String benchmarkGovernanceStatus,
    long initialDistinctTradingDays,
    long finalDistinctTradingDays,
    String historySufficiencyStatus,
    String ingestionOutcome,
    int newObservationsInserted,
    String calculationOutcome,
    Long calculationRunId,
    List<String> metricsCalculated,
    String failureOrSkipReason
) {}
