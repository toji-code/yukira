package com.yukira.backend.universe;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable evaluation result for historical NAV data sufficiency of a SchemeOption (TSK-M1-03).
 */
public record HistorySufficiencyResult(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    String amcName,
    EquityUniverseCategory category,
    boolean inReferenceUniverse,
    HistorySufficiencyStatus status,
    long distinctObservationDates,
    int requiredThreshold,
    LocalDate earliestObservationDate,
    LocalDate latestObservationDate,
    String ruleVersion,
    OffsetDateTime evaluatedAt,
    String explanation,
    List<String> metricLimitations
) {
    public static final int DEFAULT_THRESHOLD = 700;
    public static final String RULE_VERSION = "YUKIRA_V1_M1_700_DAY_THRESHOLD";

    public static final List<String> STANDARD_METRIC_LIMITATIONS = List.of(
        "Common 700-observation threshold is an operational prerequisite for 3Y multi-year metrics (e.g. RSK-01 Volatility, RET-03 CAGR).",
        "700 observations does NOT guarantee metric-specific analytical readiness.",
        "Benchmark-relative metrics (e.g. MKT-01 Beta, MKT-02 Alpha, RSK-02 Tracking Error) additionally require an assigned primary benchmark with aligned trading calendar dates.",
        "Risk-free adjusted metrics (e.g. RSK-04 Sharpe Ratio, Sortino) additionally require aligned FBIL 91-Day T-Bill yield observations.",
        "Tail-risk metrics (e.g. RSK-06 VaR, RSK-07 Expected Shortfall) require sufficient historical tail observations in the 5th percentile."
    );

    public static HistorySufficiencyResult of(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        String amcName,
        EquityUniverseCategory category,
        boolean inReferenceUniverse,
        HistorySufficiencyStatus status,
        long distinctObservationDates,
        int requiredThreshold,
        LocalDate earliestObservationDate,
        LocalDate latestObservationDate,
        String explanation
    ) {
        return new HistorySufficiencyResult(
            schemeOptionId,
            amfiCode,
            isin,
            schemeName,
            amcName,
            category,
            inReferenceUniverse,
            status,
            distinctObservationDates,
            requiredThreshold,
            earliestObservationDate,
            latestObservationDate,
            RULE_VERSION,
            OffsetDateTime.now(),
            explanation,
            STANDARD_METRIC_LIMITATIONS
        );
    }
}
