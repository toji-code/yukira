package com.yukira.backend.universe;

/**
 * Deterministic recovery classification for reference-universe scheme options (Phase A1).
 *
 * Distinguishes:
 * - SUFFICIENT_3Y: Options already satisfying the frozen 700-day 3-year threshold.
 * - STORED_ARTIFACT_READY: Options with partial history (e.g. 556 observations) whose missing 2026 data
 *   is already captured by stored authentic AMFI artifacts #5378–#5381.
 * - HISTORICAL_GAP_3Y: Options with sufficient all-time depth (>= 700) but gaps inside the strict 3Y window.
 * - OLDER_DISCONTINUITY: Options with limited all-time history (< 700) and older historical discontinuities.
 * - INCEPTION_BOUNDED: Options whose fund inception date is after the 3Y window start (2023-10-01),
 *   meaning missing observations cannot be manufactured.
 */
public enum HistoricalNavRecoveryCategory {
    ALL,
    SUFFICIENT_3Y,
    STORED_ARTIFACT_READY,
    HISTORICAL_GAP_3Y,
    OLDER_DISCONTINUITY,
    INCEPTION_BOUNDED
}
