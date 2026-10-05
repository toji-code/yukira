package com.yukira.backend.dto.portfolio;

import com.yukira.backend.dto.discovery.CriterionEvaluationDto;
import com.yukira.backend.dto.discovery.EvidenceStateDto;
import com.yukira.backend.dto.discovery.ScoreSummaryDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Goal alignment evaluation for an individual portfolio holding.
 */
public record HoldingGoalAlignmentDto(
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
    BigDecimal units,
    BigDecimal marketValue,
    BigDecimal portfolioWeightPercentage,
    String valuationStatus,
    String alignmentState,
    Map<String, CriterionEvaluationDto> criteriaMap,
    ScoreSummaryDto analyticalScore,
    EvidenceStateDto evidenceState,
    List<String> investigationQuestions
) {}
