package com.yukira.backend.scoring.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Audit and coverage statistics report for mutual fund universe analytical scoring.
 */
public record UniverseScoringReport(
    long totalSchemeOptions,
    long activeSchemeOptions,
    long supportedByMethodology,
    long successfullyScored,
    long scoreUnavailable,
    long insufficientData,
    long dataQualityLimited,
    long notApplicableOrUnsupported,
    long completeMetricCoverageCount,
    long partialMetricCoverageCount,
    Map<String, Long> topUnavailabilityReasons,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String scoreVersion,
    String summary
) {}
