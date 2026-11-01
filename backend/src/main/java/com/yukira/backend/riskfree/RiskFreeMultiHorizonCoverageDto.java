package com.yukira.backend.riskfree;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Multi-horizon historical risk-free-rate coverage profile for FBIL 91-Day Treasury Bill (Phase A5).
 * Evaluates 3Y, 5Y, and 10Y horizons relative to an explicit reference date.
 */
public record RiskFreeMultiHorizonCoverageDto(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("benchmarkName") String benchmarkName,
    @JsonProperty("provider") String provider,
    @JsonProperty("tenor") String tenor,
    @JsonProperty("daycountConvention") String daycountConvention,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("allTimeObservationCount") long allTimeObservationCount,
    @JsonProperty("allTimeEarliestDate") LocalDate allTimeEarliestDate,
    @JsonProperty("allTimeLatestDate") LocalDate allTimeLatestDate,
    @JsonProperty("freshnessStatus") String freshnessStatus,
    @JsonProperty("methodologyStatus") String methodologyStatus,
    @JsonProperty("threeYearCoverage") RiskFreeHorizonCoverageDto threeYearCoverage,
    @JsonProperty("fiveYearCoverage") RiskFreeHorizonCoverageDto fiveYearCoverage,
    @JsonProperty("tenYearCoverage") RiskFreeHorizonCoverageDto tenYearCoverage,
    @JsonProperty("evaluatedAt") OffsetDateTime evaluatedAt,
    @JsonProperty("governanceDisclaimer") String governanceDisclaimer
) {}
