package com.yukira.backend.dto.ai;

import java.util.List;
import java.util.Map;

/**
 * Typed Grounded AI Interpretation DTO.
 *
 * Epistemic Contract:
 * - Serves strictly as an evidence interpretation layer over deterministic financial calculations.
 * - Explicitly categorizes content into Facts, Interpretation, Risk Factors, Limitations, Invalidation Criteria, and Investigation Questions.
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
    String generatedAt
) {}
