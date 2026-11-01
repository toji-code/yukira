package com.yukira.backend.universe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable run summary report for a bounded reference-universe batch expansion
 * and automated metric calculation run (TSK-M1-06).
 */
public record UniverseBatchRunReportDto(
    String runId,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    long durationMs,
    boolean dryRun,
    String operator,
    String overallStatus,
    Map<String, Object> parameters,
    int totalSelected,
    int totalIngested,
    int totalCalculated,
    int totalSkipped,
    int totalFailed,
    List<BenchmarkCoverageSummaryDto> benchmarkSummaries,
    List<SchemeBatchExecutionDetailDto> schemeOutcomes,
    List<String> governanceNotes
) {}
