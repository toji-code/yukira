package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic DTO containing Portfolio Analytical Score V1 exposure-weighted aggregation output.
 * Epistemic & Product Invariants:
 * 1. Portfolio score is derived strictly from existing persisted eligible YUKIRA_SCORE_V1 fund scores.
 * 2. Coverage states (COMPLETE, PARTIAL, INSUFFICIENT_DATA, UNAVAILABLE, EMPTY) strictly prevent zero-coercion.
 * 3. NO investment advice, recommendations, BUY/HOLD/SELL signals, or composite risk grades are generated.
 */
public record PortfolioAnalyticalScoreDto(
    BigDecimal portfolioScore,
    String scoreState,
    int coveredHoldingCount,
    int totalHoldingCount,
    BigDecimal coveredPortfolioValue,
    BigDecimal totalValuedPortfolioValue,
    BigDecimal coveredPortfolioWeight,
    int excludedHoldingCount,
    String asOfDate,
    String scoreVersion,
    String methodologyStatus,
    List<ContributingHoldingScoreDto> contributingHoldings,
    List<ExcludedHoldingScoreDto> excludedHoldings
) {}
