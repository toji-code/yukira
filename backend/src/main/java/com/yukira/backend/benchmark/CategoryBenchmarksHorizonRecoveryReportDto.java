package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Audit report of bounded historical recovery across all five canonical category benchmarks (Phase A4).
 */
public record CategoryBenchmarksHorizonRecoveryReportDto(
    @JsonProperty("operation") String operation,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("horizon") String horizon,
    @JsonProperty("totalBenchmarks") int totalBenchmarks,
    @JsonProperty("benchmarksSucceeded") int benchmarksSucceeded,
    @JsonProperty("benchmarksPartial") int benchmarksPartial,
    @JsonProperty("benchmarksFailed") int benchmarksFailed,
    @JsonProperty("totalObservationsInserted") int totalObservationsInserted,
    @JsonProperty("totalIntervalsRecovered") int totalIntervalsRecovered,
    @JsonProperty("benchmarkResults") List<BenchmarkHorizonRecoveryResultDto> benchmarkResults,
    @JsonProperty("executedAt") OffsetDateTime executedAt,
    @JsonProperty("governanceNote") String governanceNote
) {}
