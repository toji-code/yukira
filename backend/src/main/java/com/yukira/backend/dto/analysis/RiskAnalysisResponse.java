package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Authoritative response contract for YUKIRA Risk & Tail analytical metrics
 * (RSK-01 Volatility, RSK-02 Downside Semideviation, RSK-03 Max Drawdown,
 *  RSK-04 Max Drawdown Duration, RSK-05 Ulcer Index).
 */
public record RiskAnalysisResponse(
    IdentityInfo identity,
    ResultInfo result,
    WindowInfo window,
    PitInfo pit,
    MethodologyInfo methodology,
    QualityInfo quality,
    ProvenanceInfo provenance,
    LimitationsInfo limitations,
    BenchmarkInfo benchmark
) {
    public record IdentityInfo(
        Long schemeId,
        String schemeName,
        String amfiCode,
        Long schemeOptionId,
        String optionType,
        String isin
    ) {}

    public record ResultInfo(
        String metricCode,
        String metricName,
        BigDecimal numericValue,
        String formattedValue,
        String units,
        String calculationStatus,
        String errorMessage
    ) {}

    public record WindowInfo(
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        Integer observationCount,
        Integer minObservationsRequired,
        Integer windowMonths
    ) {}

    public record PitInfo(
        OffsetDateTime knowledgeCutoffTime,
        boolean pitFilteringApplied,
        String temporalLimitationDisclosure,
        String cutoffConventionApplied,
        String sourceAvailabilitySemantic
    ) {}

    public record MethodologyInfo(
        String methodologyCode,
        String methodologyVersion,
        String approvalStatus,
        boolean isCandidate,
        String annualizationConvention,
        String denominatorConvention,
        String formulaDisclosure
    ) {}

    public record QualityDimension(
        String dimension,
        String state,
        String description
    ) {}

    public record QualityInfo(
        String overallAssessment,
        List<QualityDimension> dimensions,
        List<String> validationFlags
    ) {}

    public record InputObservationRef(
        Long observationId,
        LocalDate effectiveDate,
        Integer revisionSeq,
        BigDecimal navValue,
        OffsetDateTime availabilityTime,
        String qualityAssessment,
        String verificationStatus,
        String revisionStatus,
        String temporalStatus,
        String presenceStatus,
        String integrityCondition,
        String sourceAvailabilitySemantic,
        Long sourceArtifactId,
        String sourceArtifactSha256
    ) {}

    public record SourceArtifactSummary(
        Long sourceArtifactId,
        String sourceUrl,
        String sha256Hash,
        OffsetDateTime retrievalTimestamp,
        Long byteSize
    ) {}

    public record ProvenanceInfo(
        Long calculationRunId,
        String runStatus,
        OffsetDateTime executionStartedAt,
        OffsetDateTime executionCompletedAt,
        String quantEngineVersion,
        String methodologyGitCommit,
        String inputSnapshotSha256,
        List<InputObservationRef> inputObservations,
        List<SourceArtifactSummary> sourceArtifacts
    ) {}

    public record LimitationsInfo(
        boolean factualAvailabilityTimestampUnavailable,
        String analyticalCutoffConvention,
        String sourceAvailabilitySemantic,
        boolean candidateAnnualizationApplied,
        boolean candidateDenominatorApplied,
        boolean insufficientEvidence,
        Integer observationCount,
        Integer minObservationsRequired,
        String disclosureSummary
    ) {}

    public record BenchmarkInfo(
        boolean benchmarkRequired,
        Long benchmarkId,
        String benchmarkNotice
    ) {}
}
