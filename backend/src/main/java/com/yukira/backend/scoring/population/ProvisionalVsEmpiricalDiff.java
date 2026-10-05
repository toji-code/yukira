package com.yukira.backend.scoring.population;

import java.math.BigDecimal;

/**
 * Evidence-grade comparison between the ACTIVE provisional reference distribution and the
 * empirically measured peer distribution for a single canonical metric.
 *
 * Governance: this record is EVIDENCE ONLY. It never mutates, replaces or deactivates
 * INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1.
 */
public record ProvisionalVsEmpiricalDiff(
    String activeReferencePopulationCode,
    boolean activeReferenceIsProvisional,
    String disposition,

    // Provisional (active) reference values as currently configured in ScoreMethodologyConfig.
    String provisionalDirection,
    Double provisionalMin,
    Double provisionalP10,
    Double provisionalP25,
    Double provisionalMedian,
    Double provisionalP75,
    Double provisionalP90,
    Double provisionalMax,
    Double provisionalTarget,

    // Empirically measured values from canonical ELIGIBLE peer MetricResults.
    Double empiricalMin,
    Double empiricalP25,
    Double empiricalMedian,
    Double empiricalP75,
    Double empiricalP95,
    Double empiricalMax,
    Double empiricalMean,
    Double empiricalStandardDeviation,
    int empiricalSampleSize,

    // Comparable deltas (empirical minus provisional) on the shared p25 / median / p75 grid.
    Double deltaP25,
    Double deltaMedian,
    Double deltaP75,
    Double provisionalIqr,
    Double empiricalIqr,

    // Target-value metrics (MKT-01 Beta) are compared against their registered target, not a percentile grid.
    Double provisionalTargetDelta
) {
    public static ProvisionalVsEmpiricalDiff notComparable(String populationCode, String reason) {
        return new ProvisionalVsEmpiricalDiff(
            populationCode, true, reason,
            // provisionalDirection, min, p10, p25, median, p75, p90, max, target
            null, null, null, null, null, null, null, null, null,
            // empirical min, p25, median, p75, p95, max, mean, stdev
            null, null, null, null, null, null, null, null,
            0,
            // deltaP25, deltaMedian, deltaP75, provisionalIqr, empiricalIqr, provisionalTargetDelta
            null, null, null, null, null, null
        );
    }

    public static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
