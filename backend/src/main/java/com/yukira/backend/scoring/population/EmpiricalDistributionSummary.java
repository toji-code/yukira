package com.yukira.backend.scoring.population;

import java.util.List;

/**
 * Empirical distribution statistics for a single canonical quantitative metric across eligible peers.
 *
 * Every value in {@code min .. standardDeviation} is a DESCRIPTIVE cross-sectional statistic
 * computed over persisted, canonical MetricResults produced by the Python quantitative engine.
 * No financial metric is computed, re-derived, interpolated or approximated in Java.
 *
 * Outliers are never auto-deleted; every missing or excluded peer is accounted for in
 * {@code missingCount} / {@code excludedCount} and enumerated in {@code exclusionReasons}.
 */
public record EmpiricalDistributionSummary(
    String metricCode,
    String metricName,
    String unit,
    String analysisWindow,
    int sampleSize,
    Double min,
    Double p05,
    Double p25,
    Double median,
    Double p75,
    Double p95,
    Double max,
    Double mean,
    Double standardDeviation,
    int missingCount,
    int excludedCount,
    List<String> exclusionReasons
) {}
