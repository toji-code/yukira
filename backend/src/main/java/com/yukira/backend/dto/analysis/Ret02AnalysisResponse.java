package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Authoritative response contract for RET-02 Simple Period Return vertical slice.
 * Contains full auditability: identity, result, requested vs selected periods,
 * PIT parameters, candidate methodology disclosure, 6-D quality states,
 * input observation lineage, limitations, and benchmark-not-required disclosure.
 */
public record Ret02AnalysisResponse(
    IdentityInfo identity,
    ResultInfo result,
    PeriodInfo period,
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

    public record PeriodInfo(
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate selectedStartDate,
        LocalDate selectedEndDate,
        Integer startLookbackDaysUsed,
        Integer endLookbackDaysUsed,
        boolean startSubstituted,
        boolean endSubstituted
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
        String lookbackSpecification,
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
        String role,
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
        boolean candidateLookbackApplied,
        int lookbackWindowDays,
        boolean insufficientEvidence,
        String disclosureSummary
    ) {}

    public record BenchmarkInfo(
        boolean benchmarkRequired,
        Long benchmarkId,
        String benchmarkNotice
    ) {}
}
