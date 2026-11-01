package com.yukira.backend.universe;

import java.time.LocalDate;
import java.util.List;

/**
 * Metadata and empirical observation state for a reference-universe NAV recovery candidate (Phase A1).
 */
public record NavRecoveryCandidateDto(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    String amcName,
    EquityUniverseCategory category,
    long count3Y,
    long countAll,
    LocalDate earliestDate,
    LocalDate latestDate,
    LocalDate inceptionDate,
    HistoricalNavRecoveryCategory classification,
    List<HistoricalNavGapInterval> missingIntervals
) {}
