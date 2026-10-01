package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data Quality & Anomaly Center Response DTO.
 * Exposes real 6-dimensional data quality status, observation coverage, ledger continuity,
 * source artifacts, PIT compliance, and registered validation issues without synthetic scoring.
 */
public record DataQualityAuditResponse(
    AuditContext context,
    QualitySummary summary,
    LedgerContinuity ledgerContinuity,
    List<TaxonomyDimension> dimensions,
    SourceArtifactStatus sourceArtifactStatus,
    List<AnomalyItem> detectedAnomalies,
    AuditLimitations limitations
) {
    public record AuditContext(
        Long schemeOptionId,
        String schemeName,
        String amfiCode,
        String isin,
        String optionType,
        String planType,
        LocalDate ledgerStartDate,
        LocalDate ledgerEndDate,
        OffsetDateTime knowledgeCutoffTime,
        OffsetDateTime evaluatedAt
    ) {}

    public record QualitySummary(
        String overallStatus, // e.g. "AUTHORITATIVE_DATA_QUALITY_VERIFIED"
        long totalObservations,
        long validObservations,
        long missingObservations,
        long revisedObservations,
        long lookbackSubstitutionsCount
    ) {}

    public record LedgerContinuity(
        LocalDate firstEffectiveDate,
        LocalDate lastEffectiveDate,
        long calendarDaysSpan,
        long totalNavObservations,
        long expectedTradingDaysEstimate,
        double coveragePercentage,
        String nonTradingGapsExplanation
    ) {}

    public record TaxonomyDimension(
        String dimensionName,
        String status,
        String description,
        String whyItMatters,
        String evidence
    ) {}

    public record SourceArtifactStatus(
        Long artifactId,
        String sourceUrl,
        String sha256Hash,
        long byteSize,
        OffsetDateTime ingestedAt,
        String verificationStatus
    ) {}

    public record AnomalyItem(
        String issueId,
        String checkCode,
        String severity, // "DATA_LIMITATION", "ANOMALY", "NO_ISSUE"
        String whatYukiraSees,
        String whyItMatters,
        String evidence,
        String affectedPeriod,
        String status,
        String limitation
    ) {}

    public record AuditLimitations(
        String pitKnowledgeCutoff,
        String marketHolidayConvention,
        String disclaimer
    ) {}
}
