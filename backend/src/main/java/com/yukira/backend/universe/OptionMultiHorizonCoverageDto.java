package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Multi-horizon historical NAV coverage profile for a scheme option across 3Y, 5Y, and 10Y horizons (Phase A3).
 *
 * Epistemic & Investor Product Rules:
 * - Surfaces multi-cycle analytical readiness to investors with complete factual transparency.
 * - Distinguishes funds with true 5Y/10Y empirical histories from younger funds bounded by inception.
 * - Leaves missing history honestly missing; never manufactures observations.
 */
public record OptionMultiHorizonCoverageDto(
    @JsonProperty("schemeOptionId") Long schemeOptionId,
    @JsonProperty("amfiCode") String amfiCode,
    @JsonProperty("isin") String isin,
    @JsonProperty("schemeName") String schemeName,
    @JsonProperty("amcName") String amcName,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("inceptionDate") LocalDate inceptionDate,
    @JsonProperty("allTimeObservationDates") Long allTimeObservationDates,
    @JsonProperty("earliestAvailableNavDate") LocalDate earliestAvailableNavDate,
    @JsonProperty("latestAvailableNavDate") LocalDate latestAvailableNavDate,
    @JsonProperty("threeYearCoverage") OptionHorizonCoverageDto threeYearCoverage,
    @JsonProperty("fiveYearCoverage") OptionHorizonCoverageDto fiveYearCoverage,
    @JsonProperty("tenYearCoverage") OptionHorizonCoverageDto tenYearCoverage,
    @JsonProperty("evaluatedAt") OffsetDateTime evaluatedAt
) {}
