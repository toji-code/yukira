package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Phase 2R Unified 3-Year Institutional Risk-Return Profile Response.
 * Structures 16 authoritative metrics under the existing CalculationRun -> MetricResult architecture.
 */
public record AnalyticalProfileResponse(
    ProfileContext context,
    List<ProfileMetricItem> returnMetrics,
    List<ProfileMetricItem> riskMetrics,
    List<ProfileMetricItem> riskAdjustedMetrics,
    List<ProfileMetricItem> marketSensitivityMetrics,
    ProfileProvenance provenance,
    ProfileQuality quality,
    ProfileLimitations limitations
) {
    public record ProfileContext(
        Long runId,
        Long schemeOptionId,
        String schemeName,
        String amfiCode,
        String isin,
        String optionType,
        String planType,
        Long benchmarkId,
        String benchmarkName,
        String benchmarkCode,
        String riskFreeProxy,
        LocalDate startDate,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        String runStatus
    ) {}

    public record ProfileMetricItem(
        String metricCode,
        String metricName,
        String category,
        BigDecimal numericValue,
        String formattedValue,
        String units,
        String periodType,
        String calculationStatus,
        String governanceStatus,
        String formulaDisclosure,
        String interpretation,
        String limitations,
        String errorMessage,
        Map<String, Object> diagnostics
    ) {}

    public record ProfileProvenance(
        Long calculationRunId,
        String runStatus,
        String inputSnapshotSha256,
        OffsetDateTime executionStartedAt,
        OffsetDateTime executionCompletedAt,
        String engineSoftwareVersion,
        String methodologyTag,
        long navObservationCount,
        long benchmarkObservationCount,
        long riskFreeObservationCount,
        List<Ret02AnalysisResponse.InputObservationRef> sampleObservations
    ) {}

    public record ProfileQuality(
        String overallAssessment,
        List<Ret02AnalysisResponse.QualityDimension> dimensions,
        List<String> validationFlags
    ) {}

    public record ProfileLimitations(
        LocalDate analysisCutoff,
        OffsetDateTime knowledgeCutoff,
        String temporalLimitationDisclosure,
        String candidateMethodologyDisclaimer
    ) {}
}
