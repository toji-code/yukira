package com.yukira.backend.dto.discovery;

import java.util.List;
import java.util.Map;

/**
 * Analytical discovery result for a single mutual fund scheme option.
 */
public record GoalDiscoveryResultDto(
    Long schemeOptionId,
    Long schemeId,
    String fundName,
    String amcName,
    String category,
    String subcategory,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    String resultState, // ELIGIBLE, PARTIALLY_EVALUATED, INSUFFICIENT_DATA, NOT_ELIGIBLE
    Map<String, CriterionEvaluationDto> criteria,
    ScoreSummaryDto analyticalScore,
    EvidenceStateDto evidenceState,
    List<String> investigationQuestions
) {}
