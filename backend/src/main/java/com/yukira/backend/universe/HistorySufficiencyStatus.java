package com.yukira.backend.universe;

/**
 * Status of historical NAV data sufficiency for a scheme option (TSK-M1-03).
 *
 * Epistemic Rules:
 * 1. ELIGIBLE: Distinct usable trading date observations >= minimum threshold (700 for 3Y metrics).
 * 2. INSUFFICIENT_HISTORY: Observations exist but do not meet the minimum threshold (1 to 699).
 * 3. NO_HISTORY: Zero observations exist for this scheme option.
 * 4. DATA_QUALITY_LIMITED: Observations exist, but quality or provenance defects (e.g. INVALID records,
 *    missing source artifacts, or non-positive NAV values) prevent safe quantitative calculation.
 * 5. NOT_EVALUATED: Scheme option has not been evaluated or cannot be evaluated.
 */
public enum HistorySufficiencyStatus {
    ELIGIBLE,
    INSUFFICIENT_HISTORY,
    NO_HISTORY,
    DATA_QUALITY_LIMITED,
    NOT_EVALUATED
}
