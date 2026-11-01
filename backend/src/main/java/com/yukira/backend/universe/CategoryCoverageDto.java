package com.yukira.backend.universe;

/**
 * Breakdown of scheme coverage and benchmark status for an individual reference universe category.
 */
public record CategoryCoverageDto(
    EquityUniverseCategory category,
    String categoryDisplayName,
    String candidateBenchmarkCode,
    String benchmarkGovernanceStatus,
    long totalOptions,
    long sufficientOptions,
    long partialOptions,
    long zeroOptions,
    int benchmarkObservationCount,
    String benchmarkCoverageStatus
) {}
