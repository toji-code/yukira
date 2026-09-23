package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Authoritative Point-in-Time Analytical Observation Series Data Contract:
 *
 * Provides downstream quantitative metric engines with an immutable, verifiable,
 * bitemporally-filtered observation series.
 *
 * Epistemic Guardrail:
 * Every series is strictly bounded by (effectiveDate <= analysisCutoffDate)
 * AND (availabilityTime <= knowledgeCutoffTime).
 * No query without knowledgeCutoffTime is permitted.
 */
public record AnalyticalObservationSeriesDto(
    SchemeOptionIdentity identity,
    PointInTimeBounds pitBounds,
    SeriesSummary summary,
    List<AnalyticalObservationItem> observations
) {

    public record SchemeOptionIdentity(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        String planType,
        String optionType
    ) {}

    public record PointInTimeBounds(
        LocalDate startDate,
        LocalDate analysisCutoffDate,
        OffsetDateTime knowledgeCutoffTime
    ) {}

    public record SeriesSummary(
        int totalObservations,
        LocalDate firstObservationDate,
        LocalDate latestObservationDate,
        BigDecimal firstNavValue,
        BigDecimal latestNavValue,
        boolean hasContinuityGaps,
        int distinctSourceArtifactsCount
    ) {}

    public record AnalyticalObservationItem(
        Long observationId,
        LocalDate effectiveDate,
        BigDecimal navValue,
        int revisionSeq,
        boolean isLatestRevision,
        OffsetDateTime availabilityTime,
        Long sourceArtifactId,
        String sourceArtifactSha256,
        String qualityAssessment,     // Dimension 1: Quality (VALID / SUSPICIOUS / INVALID)
        String verificationStatus,    // Dimension 2: Verification (VERIFIED / UNVERIFIED)
        String revisionStatus,        // Dimension 3: Revision (ORIGINAL / REVISED / SUPERSEDED)
        String temporalStatus,        // Dimension 4: Freshness (CURRENT / STALE)
        String presenceStatus,        // Dimension 5: Presence (AVAILABLE / MISSING / NOT_APPLICABLE)
        String integrityCondition     // Dimension 6: Integrity (NONE / DUPLICATE / CONFLICTING)
    ) {}
}
