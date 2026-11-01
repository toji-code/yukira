package com.yukira.backend.dto.ai;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Structured Grounded Evidence Package.
 * Derived strictly from authoritative backend entities, calculation runs, metric results,
 * profile responses, and portfolio summaries.
 * Serves as the immutable input payload provided to the AI interpretation layer or deterministic fallback engine.
 * Contains ZERO fabricated or unverified values.
 */
public record GroundedEvidencePackageDto(
    Long schemeOptionId,
    String schemeName,
    String amfiCode,
    String isin,
    String category,
    String plan,
    String option,
    Long calculationRunId,
    String asOfDate,
    String knowledgeCutoff,
    Double overallScore,
    String scoreStatus,
    Map<String, Double> dimensionScores,
    Map<String, Object> metricValues,
    Map<String, String> dataQualityFlags,
    String methodologyStatus,
    List<String> sourceArtifactDigests,
    Map<String, Object> portfolioContext,
    String horizon,
    String windowStartDate,
    Integer observationCount,
    Integer requiredObservationThreshold,
    String benchmarkName,
    String benchmarkCode,
    String benchmarkMappingStatus,
    Map<String, Object> multiHorizonSummary,
    List<String> limitations
) {
    /**
     * Backward-compatible 18-argument constructor for existing callers and tests.
     */
    public GroundedEvidencePackageDto(
        Long schemeOptionId,
        String schemeName,
        String amfiCode,
        String isin,
        String category,
        String plan,
        String option,
        Long calculationRunId,
        String asOfDate,
        String knowledgeCutoff,
        Double overallScore,
        String scoreStatus,
        Map<String, Double> dimensionScores,
        Map<String, Object> metricValues,
        Map<String, String> dataQualityFlags,
        String methodologyStatus,
        List<String> sourceArtifactDigests,
        Map<String, Object> portfolioContext
    ) {
        this(
            schemeOptionId,
            schemeName,
            amfiCode,
            isin,
            category,
            plan,
            option,
            calculationRunId,
            asOfDate,
            knowledgeCutoff,
            overallScore,
            scoreStatus,
            dimensionScores,
            metricValues,
            dataQualityFlags,
            methodologyStatus,
            sourceArtifactDigests,
            portfolioContext,
            "3Y",
            null,
            null,
            null,
            null,
            null,
            "CANDIDATE",
            Collections.emptyMap(),
            Collections.emptyList()
        );
    }
}
