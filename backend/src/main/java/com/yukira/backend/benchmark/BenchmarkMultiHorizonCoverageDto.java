package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yukira.backend.universe.EquityUniverseCategory;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Multi-horizon historical TRI coverage profile for a canonical category benchmark (Phase A4).
 * Evaluates 3Y, 5Y, and 10Y horizons relative to an explicit reference date.
 */
public record BenchmarkMultiHorizonCoverageDto(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("benchmarkName") String benchmarkName,
    @JsonProperty("provider") String provider,
    @JsonProperty("returnVariant") String returnVariant,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("categoryDisplayName") String categoryDisplayName,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("allTimeObservationCount") long allTimeObservationCount,
    @JsonProperty("allTimeEarliestDate") LocalDate allTimeEarliestDate,
    @JsonProperty("allTimeLatestDate") LocalDate allTimeLatestDate,
    @JsonProperty("freshnessStatus") String freshnessStatus,
    @JsonProperty("methodologyStatus") String methodologyStatus,
    @JsonProperty("threeYearCoverage") BenchmarkHorizonCoverageDto threeYearCoverage,
    @JsonProperty("fiveYearCoverage") BenchmarkHorizonCoverageDto fiveYearCoverage,
    @JsonProperty("tenYearCoverage") BenchmarkHorizonCoverageDto tenYearCoverage,
    @JsonProperty("evaluatedAt") OffsetDateTime evaluatedAt,
    @JsonProperty("governanceDisclaimer") String governanceDisclaimer
) {}
