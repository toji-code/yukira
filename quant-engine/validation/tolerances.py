"""Metric-specific numerical parity tolerances for Phase 2S.

CORE GOVERNANCE RULE:
Universal numerical tolerance (such as 1e-12) is mathematically unsound and strictly prohibited.
Tolerances must be metric-specific, pre-specified, and justified based on the mathematical
characteristics of the estimator (e.g. discrete exponentiation, quantile interpolation, OLS decomposition).
"""

from __future__ import annotations

import math
from typing import NamedTuple


class ToleranceSpec(NamedTuple):
    """Specification of pre-specified metric tolerance."""
    metric_code: str
    absolute_tolerance: float
    relative_tolerance: float
    justification: str
    requires_exact_equality: bool = False


# Pre-specified, frozen tolerances for the six Phase 2S-A representative metrics
REPRESENTATIVE_TOLERANCES: dict[str, ToleranceSpec] = {
    "RET-03": ToleranceSpec(
        metric_code="RET-03",
        absolute_tolerance=1e-10,
        relative_tolerance=1e-9,
        justification=(
            "Annualized compounded return evaluates exponentiation with fractional powers "
            "(365.25 / elapsed_calendar_days). A 1e-10 tolerance accommodates standard IEEE-754 "
            "floating-point pow() transcendental approximations across compiler runtimes."
        ),
        requires_exact_equality=False,
    ),
    "RSK-01": ToleranceSpec(
        metric_code="RSK-01",
        absolute_tolerance=1e-12,
        relative_tolerance=1e-11,
        justification=(
            "Sample standard deviation with N-1 divisor and sqrt(252) annualization represents "
            "linear dispersion on float64 arrays. Machine precision tolerance (1e-12) is achievable."
        ),
        requires_exact_equality=False,
    ),
    "RSK-03": ToleranceSpec(
        metric_code="RSK-03",
        absolute_tolerance=1e-12,
        relative_tolerance=1e-11,
        justification=(
            "Maximum drawdown is a sequential path scan of running peaks and simple ratios. "
            "Under identical float64 input sequences, peak comparisons achieve 1e-12 machine precision."
        ),
        requires_exact_equality=False,
    ),
    "RSK-06": ToleranceSpec(
        metric_code="RSK-06",
        absolute_tolerance=1e-12,
        relative_tolerance=1e-11,
        justification=(
            "Historical VaR 95% is verified to use the exact continuous linear empirical quantile convention "
            "with (N-1) rank positioning and positive loss magnitude convention. Under identical continuous "
            "linear interpolation, production and independent reference match to 1e-12 machine precision."
        ),
        requires_exact_equality=False,
    ),
    "RAT-01": ToleranceSpec(
        metric_code="RAT-01",
        absolute_tolerance=1e-8,
        relative_tolerance=1e-7,
        justification=(
            "Sharpe ratio combines synchronized daily risk-free yield curve subtractions, "
            "excess-return sample means, and sample standard deviations. A 1e-8 tolerance "
            "accounts for floating-point accumulation ordering in excess-return summations."
        ),
        requires_exact_equality=False,
    ),
    "REL-01": ToleranceSpec(
        metric_code="REL-01",
        absolute_tolerance=1e-8,
        relative_tolerance=1e-7,
        justification=(
            "Beta requires bivariate OLS excess-return covariance over benchmark variance. "
            "Different linear algebra routines (SVD vs QR vs direct two-pass mean centering) "
            "legitimately diverge up to 1e-8 on correlated 3-year daily return series."
        ),
        requires_exact_equality=False,
    ),
}


def get_tolerance_spec(metric_code: str) -> ToleranceSpec:
    """Retrieve pre-specified tolerance specification for a metric."""
    if metric_code not in REPRESENTATIVE_TOLERANCES:
        raise ValueError(
            f"Metric {metric_code} is not one of the authorized Phase 2S-A representative metrics: "
            f"{list(REPRESENTATIVE_TOLERANCES.keys())}"
        )
    return REPRESENTATIVE_TOLERANCES[metric_code]


def assess_parity(
    metric_code: str,
    production_value: float,
    reference_value: float,
) -> tuple[bool, float, str]:
    """
    Assess numerical parity between production and independent reference calculations.

    Returns:
        (passed, discrepancy, explanation)
    """
    spec = get_tolerance_spec(metric_code)

    if not math.isfinite(production_value) or not math.isfinite(reference_value):
        if math.isnan(production_value) and math.isnan(reference_value):
            return True, 0.0, "Both values are NaN (controlled non-finite outcome)"
        return False, float("nan"), "One or both values are non-finite"

    discrepancy = abs(production_value - reference_value)

    if spec.requires_exact_equality:
        passed = (production_value == reference_value)
        explanation = (
            f"Exact equality required. Discrepancy: {discrepancy:.2e}"
            if not passed else "Exact equality verified."
        )
        return passed, discrepancy, explanation

    # Check absolute tolerance
    if discrepancy <= spec.absolute_tolerance:
        return True, discrepancy, f"Passed absolute tolerance {spec.absolute_tolerance:.1e}"

    # Check relative tolerance for non-zero magnitudes
    magnitude = max(abs(production_value), abs(reference_value))
    if magnitude > 0:
        rel_diff = discrepancy / magnitude
        if rel_diff <= spec.relative_tolerance:
            return True, discrepancy, f"Passed relative tolerance {spec.relative_tolerance:.1e}"

    return (
        False,
        discrepancy,
        f"Failed pre-specified tolerance (abs: {spec.absolute_tolerance:.1e}, rel: {spec.relative_tolerance:.1e}). "
        f"Production: {production_value:.8f}, Reference: {reference_value:.8f}, Discrepancy: {discrepancy:.8e}"
    )
