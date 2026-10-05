package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Deterministic DTO containing descriptive concentration analytics for an investor portfolio.
 * Epistemic Guardrail: Strictly descriptive. Never contains risk warnings, quality ratings, or investment advice.
 */
public record ConcentrationAnalysisDto(
    String topHoldingName,
    Long topHoldingSchemeOptionId,
    BigDecimal topHoldingWeight,
    BigDecimal top3HoldingsWeight,
    BigDecimal top5HoldingsWeight,
    String topAmcName,
    BigDecimal topAmcWeight,
    String topCategoryName,
    BigDecimal topCategoryWeight,
    String concentrationState
) {}
