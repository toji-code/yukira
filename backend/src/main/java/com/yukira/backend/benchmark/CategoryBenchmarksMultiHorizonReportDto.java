package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Institutional audit report of multi-horizon historical TRI coverage across all five canonical
 * category benchmarks for the reference universe (Phase A4).
 */
public record CategoryBenchmarksMultiHorizonReportDto(
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("ruleVersion") String ruleVersion,
    @JsonProperty("totalBenchmarksEvaluated") int totalBenchmarksEvaluated,
    @JsonProperty("benchmarks") List<BenchmarkMultiHorizonCoverageDto> benchmarks,
    @JsonProperty("horizonSummary") Map<String, HorizonSummaryMetrics> horizonSummary,
    @JsonProperty("evaluatedAt") OffsetDateTime evaluatedAt,
    @JsonProperty("governanceDisclaimer") String governanceDisclaimer
) {
    public record HorizonSummaryMetrics(
        @JsonProperty("horizonCode") String horizonCode,
        @JsonProperty("benchmarksWithFullCoverage") int benchmarksWithFullCoverage,
        @JsonProperty("benchmarksWithPartialCoverage") int benchmarksWithPartialCoverage,
        @JsonProperty("benchmarksWithMissingHistory") int benchmarksWithMissingHistory,
        @JsonProperty("totalPersistedObservations") long totalPersistedObservations
    ) {}
}
