package com.yukira.backend.dto.portfolio.risk.stress;

/**
 * Standardized reason codes for withheld, unavailable, or numerically invalid
 * historical stress calculations (Tasks 60 & 75).
 *
 * Epistemic Guardrails:
 * - Differentiates between missing source data, insufficient observation counts,
 *   truncated constituent inception history, numerical domain invalidity,
 *   and reconciliation discrepancies.
 * - Prevents generic "No data" or fallback zero returns from masking calculation failures.
 */
public enum StressWithholdingReasonCode {
    ELIGIBLE_CALCULATED("Calculated successfully over eligible historical observation window."),
    INCOMPLETE_CONSTITUENT_HISTORY("One or more portfolio constituents lack NAV observations due to fund inception dates post-dating the stress episode. Aggregate portfolio proxy withheld to prevent synthetic weight extrapolation."),
    INSUFFICIENT_OBSERVATIONS("Observation count in synchronized trading window is below the minimum required for statistical reliability."),
    NUMERICAL_INVALIDITY_NEGATIVE_WEALTH("Portfolio wealth path dropped to zero or negative values during evaluation window, rendering logarithmic compounding undefined."),
    NUMERICAL_INVALIDITY_TERMINAL_TOTAL_LOSS("Portfolio experienced a 100% terminal loss (zero wealth), terminating downstream logarithmic attribution."),
    NUMERICAL_INVALIDITY_RETURN_BELOW_MINUS_ONE("Return observation below -100% detected, violating long-only unleveraged portfolio domain."),
    NUMERICAL_INVALIDITY_NON_FINITE_RETURN("Non-finite (NaN or Infinite) return observation encountered in constituent series."),
    NUMERICAL_INVALIDITY_SINGULAR_LOG("Logarithmic linking factor evaluation failed due to non-positive wealth argument (1 + R <= 0)."),
    RECONCILIATION_DISCREPANCY("Carino additive constituent attribution sum diverged from cumulative portfolio proxy return beyond ±0.0001% tolerance."),
    UNAVAILABLE_SOURCE_DATA("Reference benchmark or constituent series observations are completely missing for the requested window."),
    METHODOLOGY_LIMITATION("Calculation withheld due to methodology policy constraints.");

    private final String defaultInvestorExplanation;

    StressWithholdingReasonCode(String defaultInvestorExplanation) {
        this.defaultInvestorExplanation = defaultInvestorExplanation;
    }

    public String getDefaultInvestorExplanation() {
        return defaultInvestorExplanation;
    }
}

