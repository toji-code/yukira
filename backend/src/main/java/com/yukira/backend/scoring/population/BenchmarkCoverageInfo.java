package com.yukira.backend.scoring.population;

import java.time.LocalDate;
import java.util.List;

/**
 * Verified coverage of the canonical TRI benchmark and risk-free series over the
 * calibration window. Relative, capture and risk-adjusted peer metrics are only
 * defensible when this coverage is stated explicitly.
 */
public record BenchmarkCoverageInfo(
    String benchmarkCode,
    Long benchmarkId,
    boolean benchmarkRegistered,
    String returnVariant,
    LocalDate firstObservationDate,
    LocalDate lastObservationDate,
    int benchmarkObservationCount,
    int minimumRequiredObservationCount,

    String riskFreeCode,
    LocalDate riskFreeFirstObservationDate,
    LocalDate riskFreeLastObservationDate,
    int riskFreeObservationCount,

    LocalDate analysisStartDate,
    LocalDate analysisEndDate,
    List<String> coverageGaps
) {}
