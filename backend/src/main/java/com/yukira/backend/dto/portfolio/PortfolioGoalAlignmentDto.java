package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate Portfolio Goal Alignment & Horizon Planning result.
 */
public record PortfolioGoalAlignmentDto(
    PortfolioGoalAlignmentRequest goalRequirements,
    String coverageState,
    int totalHoldingsCount,
    int valuedHoldingsCount,
    int alignedHoldingsCount,
    int partiallyEvaluatedHoldingsCount,
    int notAlignedHoldingsCount,
    int insufficientDataHoldingsCount,
    BigDecimal summaryTotalValue,
    BigDecimal coveredPortfolioValue,
    BigDecimal alignedPortfolioWeightPercentage,
    BigDecimal partiallyEvaluatedPortfolioWeightPercentage,
    BigDecimal notAlignedPortfolioWeightPercentage,
    BigDecimal insufficientDataPortfolioWeightPercentage,
    BigDecimal unknownExposurePercentage,
    List<HoldingGoalAlignmentDto> holdingEvaluations,
    List<String> investigationQuestions,
    List<String> limitations
) {}
