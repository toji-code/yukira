"""Descriptive-only dependency and collinearity analysis for Phase 2S-A.

STRICT GOVERNANCE RESTRICTIONS:
Dependency analysis is strictly informational and descriptive audit context.
It MUST NOT:
- Downweight metrics in analytical presentations
- Modify, adjust, or composite metric values
- Automatically reject or invalidate metrics
- Alter frozen quantitative methodology
- Generate scoring rules, portfolio weights, or investment recommendations
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class DependencyEvidence:
    """Descriptive-only audit report of mathematical and empirical dependencies."""
    metric_code: str
    mathematical_inputs: list[str]
    structural_relationships: list[str]
    collinearity_notes: list[str]
    governance_prohibitions_confirmed: bool = True

    def to_dict(self) -> dict[str, Any]:
        return {
            "metric_code": self.metric_code,
            "mathematical_inputs": self.mathematical_inputs,
            "structural_relationships": self.structural_relationships,
            "collinearity_notes": self.collinearity_notes,
            "governance_prohibitions_confirmed": self.governance_prohibitions_confirmed,
        }


# Descriptive dependency map for the six representative metrics
REPRESENTATIVE_DEPENDENCIES: dict[str, DependencyEvidence] = {
    "RET-03": DependencyEvidence(
        metric_code="RET-03",
        mathematical_inputs=["start_nav (P_0)", "end_nav (P_T)", "elapsed_calendar_days (D_cal)"],
        structural_relationships=[
            "Non-linear power compounding: R_cagr = (P_T / P_0)^(365.25 / D_cal) - 1.",
            "Functionally independent of intermediate return volatility or path sequences.",
            "Direct mathematical input into annualized excess return models.",
        ],
        collinearity_notes=[
            "Uncorrelated with maximum drawdown timing or duration.",
            "High empirical rank correlation with simple period return (RET-02) over fixed windows.",
        ],
    ),
    "RSK-01": DependencyEvidence(
        metric_code="RSK-01",
        mathematical_inputs=["daily_nav_observations (P_t)", "periods_per_year (252)"],
        structural_relationships=[
            "Unbiased sample standard deviation of logarithmic returns annualized by sqrt(252).",
            "Serves as the explicit denominator in the annualized Sharpe ratio (RAT-01).",
            "Symmetric measure that treats upside return variance identically to downside variance.",
        ],
        collinearity_notes=[
            "High collinearity with Downside Semideviation (RSK-02) under near-symmetric return distributions.",
            "Diverges significantly from Downside Deviation in negatively skewed or fat-tailed portfolios.",
        ],
    ),
    "RSK-03": DependencyEvidence(
        metric_code="RSK-03",
        mathematical_inputs=["chronological_nav_series (P_t)"],
        structural_relationships=[
            "Purely path-dependent cumulative peak-to-trough scan: min_t (P_t / max_{s <= t} P_s - 1).",
            "Serves as the mathematical envelope bounding Calmar ratio and Ulcer Index (RSK-05).",
            "Invariant to cash flow scaling or total portfolio size.",
        ],
        collinearity_notes=[
            "Moderate empirical correlation with annualized volatility (RSK-01); volatility measures dispersion around mean, while drawdown measures cumulative loss streaks.",
        ],
    ),
    "RSK-06": DependencyEvidence(
        metric_code="RSK-06",
        mathematical_inputs=["daily_periodic_returns (r_t)", "confidence_level (0.95)"],
        structural_relationships=[
            "Non-parametric quantile estimator on empirical return distribution: -q_{0.05}.",
            "Lower-bound threshold defining the integration boundary for Expected Shortfall (RSK-07).",
            "Dimensionless daily percentage loss magnitude.",
        ],
        collinearity_notes=[
            "Rank-correlated with standard deviation under Gaussian assumptions, but captures non-linear tail fatness and negative skewness.",
        ],
    ),
    "RAT-01": DependencyEvidence(
        metric_code="RAT-01",
        mathematical_inputs=[
            "portfolio_returns (r_{p,t})",
            "fbil_risk_free_rates (r_{f,t})",
            "annualized_volatility (RSK-01)",
        ],
        structural_relationships=[
            "Composite estimator: excess return mean divided by sample standard deviation times sqrt(252).",
            "Inversely proportional to total risk: doubling volatility halves the Sharpe ratio for fixed excess return.",
            "Directly dependent on FBIL 91-Day T-bill risk-free yield curve accrual accuracy.",
        ],
        collinearity_notes=[
            "Under compressed or negative excess returns, higher volatility mathematically increases the Sharpe ratio toward zero (a well-known failure mode requiring prominent disclosure).",
        ],
    ),
    "REL-01": DependencyEvidence(
        metric_code="REL-01",
        mathematical_inputs=[
            "portfolio_excess_returns (r_p - r_f)",
            "benchmark_excess_returns (r_b - r_f)",
        ],
        structural_relationships=[
            "Bivariate OLS slope coefficient: Cov(r_p - r_f, r_b - r_f) / Var(r_b - r_f).",
            "Direct denominator in the Treynor ratio (RAT-02: excess return per unit of systematic risk).",
            "Undefined when benchmark returns have zero variance.",
        ],
        collinearity_notes=[
            "Related to correlation (COR-01) via beta = correlation * (std_p / std_b).",
            "Captures linear systematic co-movement; uninformative on unhedged non-linear factor exposures.",
        ],
    ),
}


def get_descriptive_dependency(metric_code: str) -> DependencyEvidence:
    """Retrieve descriptive dependency evidence for a representative metric."""
    if metric_code not in REPRESENTATIVE_DEPENDENCIES:
        raise ValueError(f"Metric {metric_code} is not one of the authorized Phase 2S-A representative metrics")
    return REPRESENTATIVE_DEPENDENCIES[metric_code]
