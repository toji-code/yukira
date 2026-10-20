package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Deterministic response payload for Portfolio Scenario Lab.
 */
public record ScenarioLabResponseDto(
    String status, // SENSITIVITY_ESTIMATE_ONLY, DATA_UNAVAILABLE, PARTIAL_COVERAGE
    String scenarioType,
    String scenarioName,
    LocalDate asOfDate,
    LocalDate snapshotDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode,
    String benchmarkName,
    BigDecimal portfolioBeta,
    Integer betaObservationCount,
    Integer betaEstimationWindowDays,
    BigDecimal estimatedPortfolioImpactPct,
    BigDecimal coveredWeightPct,
    BigDecimal unmodeledWeightPct,
    boolean isFullyCovered,
    String unmodeledTreatment,
    List<ScenarioConstituentImpactDto> constituentImpacts,
    List<ScenarioExposureImpactDto> exposureImpacts,
    Map<String, String> epistemicClassifications,
    List<String> methodologicalAssumptions,
    List<String> limitations,
    List<String> investigationQuestions,
    String methodologyVersion
) {}
