package com.yukira.backend.scoring.population;

import java.math.BigDecimal;

/**
 * Read-only projection over persisted canonical MetricResults for peer calibration.
 * Values are produced exclusively by the Python quantitative engine; no value is
 * derived, interpolated or approximated in Java.
 */
public interface PeerMetricResultProjection {

    String getMetricCode();

    String getPeriodType();

    BigDecimal getNumericValue();

    String getUnits();

    String getCalculationStatus();

    Long getCalculationRunId();

    Long getSchemeOptionId();
}
