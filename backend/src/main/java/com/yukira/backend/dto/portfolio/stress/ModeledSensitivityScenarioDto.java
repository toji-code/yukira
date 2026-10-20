package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;
import java.util.List;

/**
 * Modeled sensitivity scenario (e.g. broad market decline) based on empirical OLS beta,
 * clearly labeled with SENSITIVITY_ESTIMATE_ONLY and explicit omitted effects.
 */
public record ModeledSensitivityScenarioDto(
    String scenarioId,
    String shockLabel,
    BigDecimal benchmarkShockPct,
    BigDecimal estimatedPortfolioLossPct,
    BigDecimal betaUsed,
    String epistemicStatus,
    List<ConstituentSensitivityDto> constituentEstimates,
    List<String> omittedEffects
) {}
