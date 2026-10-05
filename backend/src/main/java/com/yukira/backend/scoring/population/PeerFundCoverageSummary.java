package com.yukira.backend.scoring.population;

import java.time.LocalDate;

/**
 * Historical coverage and data quality assessment for an individual peer candidate fund.
 */
public record PeerFundCoverageSummary(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String amcCode,
    String schemeName,
    int observationCount,
    LocalDate firstObservationDate,
    LocalDate lastObservationDate,
    String status,
    int gapDaysCount,
    int duplicateCount,
    boolean meetsThreshold,
    String sourceHash
) {}
