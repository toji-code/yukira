package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Detailed factual coverage assessment for a scheme option in a specific analytical horizon (Phase A3).
 *
 * Epistemic Rules:
 * - Separates actual observation depth from horizon sufficiency.
 * - Inception bounds are explicitly flagged: funds younger than the horizon cannot physically qualify.
 * - Remaining gaps are enumerated without synthetic interpolation or forward-filling.
 * - Methodology status explicitly distinguishes frozen/approved 3Y from candidate 5Y/10Y.
 */
public record OptionHorizonCoverageDto(
    @JsonProperty("schemeOptionId") Long schemeOptionId,
    @JsonProperty("amfiCode") String amfiCode,
    @JsonProperty("isin") String isin,
    @JsonProperty("schemeName") String schemeName,
    @JsonProperty("amcName") String amcName,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("horizon") AnalyticalHorizon horizon,
    @JsonProperty("windowStart") LocalDate windowStart,
    @JsonProperty("windowEnd") LocalDate windowEnd,
    @JsonProperty("inceptionDate") LocalDate inceptionDate,
    @JsonProperty("isInceptionBounded") boolean isInceptionBounded,
    @JsonProperty("distinctObservationDatesInHorizon") long distinctObservationDatesInHorizon,
    @JsonProperty("requiredThreshold") int requiredThreshold,
    @JsonProperty("isSufficient") boolean isSufficient,
    @JsonProperty("coverageStatus") String coverageStatus,
    @JsonProperty("methodologyStatus") String methodologyStatus,
    @JsonProperty("earliestObservationDate") LocalDate earliestObservationDate,
    @JsonProperty("latestObservationDate") LocalDate latestObservationDate,
    @JsonProperty("allTimeObservationDates") Long allTimeObservationDates,
    @JsonProperty("remainingGapCount") int remainingGapCount,
    @JsonProperty("missingIntervals") List<HistoricalNavGapInterval> missingIntervals,
    @JsonProperty("explanation") String explanation
) {}
