package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yukira.backend.universe.AnalyticalHorizon;
import com.yukira.backend.universe.EquityUniverseCategory;

import java.time.LocalDate;
import java.util.List;

/**
 * Factual historical TRI coverage assessment for a canonical category benchmark across an explicit horizon (Phase A4).
 *
 * Epistemic Rules:
 * - Surfaces factual observation counts, available history span, and missing intervals.
 * - Strictly decouples:
 *     1. Historical coverage (observation presence across the requested calendar window)
 *     2. Source freshness (recency relative to the current market trading calendar)
 *     3. Methodology status (candidate vs approved; Phase 2H freeze preserved)
 * - Benchmark coverage does NOT adopt NAV-specific 700-day thresholds unless explicitly mandated.
 * - Leaves missing history honestly missing; never manufactures synthetic observations.
 */
public record BenchmarkHorizonCoverageDto(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("benchmarkName") String benchmarkName,
    @JsonProperty("provider") String provider,
    @JsonProperty("returnVariant") String returnVariant,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("categoryDisplayName") String categoryDisplayName,
    @JsonProperty("horizon") AnalyticalHorizon horizon,
    @JsonProperty("horizonCode") String horizonCode,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("windowStart") LocalDate windowStart,
    @JsonProperty("windowEnd") LocalDate windowEnd,
    @JsonProperty("distinctObservationDates") long distinctObservationDates,
    @JsonProperty("earliestObservationDate") LocalDate earliestObservationDate,
    @JsonProperty("latestObservationDate") LocalDate latestObservationDate,
    @JsonProperty("allTimeObservationCount") long allTimeObservationCount,
    @JsonProperty("allTimeEarliestDate") LocalDate allTimeEarliestDate,
    @JsonProperty("allTimeLatestDate") LocalDate allTimeLatestDate,
    @JsonProperty("historySpanDays") long historySpanDays,
    @JsonProperty("missingIntervalCount") int missingIntervalCount,
    @JsonProperty("missingIntervals") List<BenchmarkGapInterval> missingIntervals,
    @JsonProperty("hasLeadingGap") boolean hasLeadingGap,
    @JsonProperty("hasIntermediateGap") boolean hasIntermediateGap,
    @JsonProperty("hasTrailingGap") boolean hasTrailingGap,
    @JsonProperty("coverageStatus") String coverageStatus, // FULL_COVERAGE, PARTIAL_COVERAGE, MISSING_LEADING_HISTORY, NO_OBSERVATIONS
    @JsonProperty("freshnessStatus") String freshnessStatus, // FRESH, STALE, PARTIAL, UNAVAILABLE, UNKNOWN
    @JsonProperty("methodologyStatus") String methodologyStatus, // CANDIDATE_METHODOLOGY_MAPPING
    @JsonProperty("explanation") String explanation,
    @JsonProperty("governanceDisclaimer") String governanceDisclaimer
) {}
