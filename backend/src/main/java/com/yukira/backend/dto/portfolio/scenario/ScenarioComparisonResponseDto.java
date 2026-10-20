package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic comparison between two scenario lab runs under identical portfolio conditions.
 */
public record ScenarioComparisonResponseDto(
    boolean isComparable,
    String comparabilityNotice,
    ScenarioLabResponseDto scenarioAResult,
    ScenarioLabResponseDto scenarioBResult,
    BigDecimal portfolioImpactDeltaPct, // B - A
    List<ScenarioConstituentDeltaDto> constituentDeltas,
    List<ScenarioExposureDeltaDto> exposureDeltas,
    List<String> methodologyDifferences,
    String divergenceSummary
) {}
