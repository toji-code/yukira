package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Audit evaluating availability of transaction-level turnover inputs vs descriptive composition changes.
 *
 * Epistemic Guardrail:
 * Gated strictly with TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING when
 * transaction logs, purchases, sales, and average net assets are unavailable.
 */
public record TurnoverEvaluationDto(
    String turnoverStatus,
    String turnoverReason,
    boolean isTurnoverCalculated,
    BigDecimal reportedRegulatoryTurnoverRatioPct,
    String descriptiveChangeFormula,
    BigDecimal averageMonthlyCompositionChangePct,
    BigDecimal annualizedDescriptiveChangePct,
    int comparableTransitionsCount,
    int totalTransitionsCount,
    List<String> limitationsAndDisclaimers
) {}
