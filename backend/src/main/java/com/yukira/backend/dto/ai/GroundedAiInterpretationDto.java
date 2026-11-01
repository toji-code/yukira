package com.yukira.backend.dto.ai;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Typed Grounded AI Interpretation DTO.
 *
 * Epistemic Contract:
 * - Serves strictly as an evidence interpretation layer over deterministic financial calculations.
 * - Explicitly categorizes content into Executive Summary, Factual Observations, Return Dynamics,
 *   Risk & Downside, Conflicting Evidence, Horizon-Dependent Analysis, Data Quality Caveats,
 *   Invalidation Factors, and Prioritized Allocator Due-Diligence Questions.
 * - Enforces structured claim-to-evidence association (EvidenceClaimDto) linking claims directly
 *   to authoritative metric codes and methodology governance statuses.
 * - Distinguishes LLM-generated output from deterministic rule-engine fallback.
 * - Contains zero investment advice, return predictions, or buy/sell/hold recommendations.
 */
public record GroundedAiInterpretationDto(
    String summary,
    List<String> whatHappened,
    List<String> interpretation,
    List<String> riskFactors,
    List<String> dataQualityCaveats,
    List<String> invalidationFactors,
    List<String> investigationQuestions,
    Map<String, Object> evidenceReferences,
    Long calculationRunId,
    Long schemeOptionId,
    String methodologyVersion,
    String asOfDate,
    String knowledgeCutoff,
    List<String> sourceArtifacts,
    String epistemicStatus,
    boolean isFallback,
    String modelProvider,
    String generatedAt,
    String horizon,
    List<EvidenceClaimDto> claims,
    List<String> returnCharacteristics,
    List<String> riskCharacteristics,
    List<String> horizonInterpretation,
    List<String> conflictingEvidence
) {
    /**
     * Backward-compatible 23-argument constructor.
     */
    public GroundedAiInterpretationDto(
        String summary,
        List<String> whatHappened,
        List<String> interpretation,
        List<String> riskFactors,
        List<String> dataQualityCaveats,
        List<String> invalidationFactors,
        List<String> investigationQuestions,
        Map<String, Object> evidenceReferences,
        Long calculationRunId,
        Long schemeOptionId,
        String methodologyVersion,
        String asOfDate,
        String knowledgeCutoff,
        List<String> sourceArtifacts,
        String epistemicStatus,
        boolean isFallback,
        String modelProvider,
        String generatedAt,
        String horizon,
        List<EvidenceClaimDto> claims,
        List<String> returnCharacteristics,
        List<String> riskCharacteristics,
        List<String> horizonInterpretation
    ) {
        this(
            summary,
            whatHappened,
            interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceReferences,
            calculationRunId,
            schemeOptionId,
            methodologyVersion,
            asOfDate,
            knowledgeCutoff,
            sourceArtifacts,
            epistemicStatus,
            isFallback,
            modelProvider,
            generatedAt,
            horizon,
            claims,
            returnCharacteristics,
            riskCharacteristics,
            horizonInterpretation,
            Collections.emptyList()
        );
    }

    /**
     * Backward-compatible 18-argument constructor for existing callers and tests.
     */
    public GroundedAiInterpretationDto(
        String summary,
        List<String> whatHappened,
        List<String> interpretation,
        List<String> riskFactors,
        List<String> dataQualityCaveats,
        List<String> invalidationFactors,
        List<String> investigationQuestions,
        Map<String, Object> evidenceReferences,
        Long calculationRunId,
        Long schemeOptionId,
        String methodologyVersion,
        String asOfDate,
        String knowledgeCutoff,
        List<String> sourceArtifacts,
        String epistemicStatus,
        boolean isFallback,
        String modelProvider,
        String generatedAt
    ) {
        this(
            summary,
            whatHappened,
            interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceReferences,
            calculationRunId,
            schemeOptionId,
            methodologyVersion,
            asOfDate,
            knowledgeCutoff,
            sourceArtifacts,
            epistemicStatus,
            isFallback,
            modelProvider,
            generatedAt,
            "3Y",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
}
