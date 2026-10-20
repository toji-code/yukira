package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.util.List;

public record HoldingsCoverageReportDto(
    LocalDate asOfDate,
    long totalActiveUniverse,
    long activeWithNav,
    long active3yReady,
    long activeWithHoldings,
    long productionVerifiedHoldingsCount,
    long controlledFixtureHoldingsCount,
    long activeWithLatestHoldings,
    long activeWithRecentHoldings,
    long activeWithValidHoldings,
    long activeWithSecurityIdentities,
    HdfcPilotAuditDto hdfcPilotAudit,
    List<SourceClassificationDto> sourceClassifications,
    AuditPerformanceMetrics performanceMetrics
) {
    public record HdfcPilotAuditDto(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        LocalDate portfolioDate,
        Long snapshotId,
        int positionCount,
        double totalDisclosedWeightPct,
        int resolvedIdentityCount,
        int unresolvedIdentityCount,
        int duplicateSecurityCount,
        Long sourceArtifactId,
        String storageUri,
        String sha256Hash,
        String availabilityTime,
        int revisionSeq,
        String freshnessState,
        String qualityState,
        String provenanceType
    ) {}

    public record SourceClassificationDto(
        String amfiCode,
        String schemeName,
        String amcName,
        String sourceState,
        String provenanceType,
        String sourceUrl,
        LocalDate lastReportingDate,
        int positionCount,
        String qualityState,
        String httpVerificationStatus
    ) {}

    public record AuditPerformanceMetrics(
        long optionsScanned,
        long queryCount,
        long durationMs,
        long peakMemoryBytes
    ) {}
}

