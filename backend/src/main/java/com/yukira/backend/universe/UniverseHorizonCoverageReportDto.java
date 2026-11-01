package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Universe-wide coverage and maturity report for a specific analytical horizon (Phase A3).
 *
 * Epistemic Rules:
 * - Aggregates all 193 reference-universe Direct-Growth options in the 5 core equity categories.
 * - Categorizes each fund deterministically by observation sufficiency and inception constraints.
 * - Governed by explicit methodology status (APPROVED for 3Y, CANDIDATE for 5Y/10Y).
 */
public record UniverseHorizonCoverageReportDto(
    @JsonProperty("horizon") AnalyticalHorizon horizon,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("windowStart") LocalDate windowStart,
    @JsonProperty("windowEnd") LocalDate windowEnd,
    @JsonProperty("threshold") int threshold,
    @JsonProperty("methodologyStatus") String methodologyStatus,
    @JsonProperty("ruleVersion") String ruleVersion,
    @JsonProperty("totalReferenceOptions") int totalReferenceOptions,
    @JsonProperty("sufficientCount") int sufficientCount,
    @JsonProperty("inceptionBoundedCount") int inceptionBoundedCount,
    @JsonProperty("partialWithGapsCount") int partialWithGapsCount,
    @JsonProperty("insufficientHistoryCount") int insufficientHistoryCount,
    @JsonProperty("noHistoryCount") int noHistoryCount,
    @JsonProperty("categoryBreakdown") Map<String, Integer> categoryBreakdown,
    @JsonProperty("candidates") List<OptionHorizonCoverageDto> candidates,
    @JsonProperty("evaluatedAt") OffsetDateTime evaluatedAt
) {}
