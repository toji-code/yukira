package com.yukira.backend.universe;

import java.time.LocalDate;

/**
 * Summary of canonical category benchmark data coverage, observation counts, and governance status.
 */
public record BenchmarkCoverageSummaryDto(
    String benchmarkCode,
    String benchmarkName,
    String provider,
    String returnVariant,
    boolean isTri,
    EquityUniverseCategory category,
    String methodologyStatus,
    int observationCount,
    LocalDate earliestDate,
    LocalDate latestDate,
    String coverageStatus
) {}
