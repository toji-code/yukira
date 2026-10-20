package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Metadata about the portfolio's available dimensions, current holdings coverage,
 * sector distribution, beta estimation, and supported templates.
 */
public record ScenarioDimensionMetadataDto(
    BigDecimal largeCapWeightPct,
    BigDecimal midCapWeightPct,
    BigDecimal smallCapWeightPct,
    BigDecimal cashWeightPct,
    BigDecimal unclassifiedWeightPct,
    BigDecimal coveredWeightPct,
    Map<String, BigDecimal> sectorWeightsPct,
    BigDecimal portfolioBeta,
    Integer betaObservationCount,
    String benchmarkCode,
    String benchmarkName,
    boolean betaAvailable,
    LocalDate closestSnapshotDate,
    long daysMismatchFromAsOf,
    String snapshotComparabilityState,
    List<ScenarioTemplateDto> supportedTemplates
) {}
