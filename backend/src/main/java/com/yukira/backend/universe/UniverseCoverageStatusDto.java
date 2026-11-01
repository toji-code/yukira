package com.yukira.backend.universe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable report detailing reference universe scheme history coverage and benchmark status
 * across all five core equity categories (TSK-M1-06).
 */
public record UniverseCoverageStatusDto(
    String ruleVersion,
    long totalMasterOptions,
    long totalReferenceUniverseOptions,
    long totalSufficientOptions,
    long totalPartialOptions,
    long totalZeroOptions,
    Map<String, CategoryCoverageDto> categoryBreakdown,
    List<BenchmarkCoverageSummaryDto> benchmarkCoverage,
    OffsetDateTime evaluatedAt,
    List<String> governanceNotes
) {}
