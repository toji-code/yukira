"""Metric-specific statistical uncertainty and resampling engine for Phase 2S-A.

CORE EPISTEMIC RULES:
1. Uncertainty methodology is metric-specific. Do NOT force one method across all metrics.
2. Distributional return metrics (RSK-01, RAT-01, REL-01, RSK-06) use block bootstrap where justified.
3. Block length MUST NOT be universally hardcoded; it must be derived from observed serial dependence.
4. Path-dependent metrics (RSK-03) MUST NOT inherit bootstrap resampling (scrambles path dependency).
5. All calculations must support deterministic execution via fixed random seeds.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, Sequence
import numpy as np

from validation.models import EvidenceOutcome
from validation.reference_kernels import (
    ref_volatility,
    ref_historical_var,
    ref_sharpe_ratio,
    ref_beta,
)


@dataclass
class UncertaintyResult:
    """Structured report of statistical uncertainty evaluation."""
    metric_code: str
    outcome: EvidenceOutcome
    methodology: str
    point_estimate: float | None = None
    standard_error: float | None = None
    ci_lower_95: float | None = None
    ci_upper_95: float | None = None
    block_length: int | None = None
    block_length_rationale: str = ""
    disclosures: list[str] = None  # type: ignore

    def __post_init__(self):
        if self.disclosures is None:
            self.disclosures = []

    def to_dict(self) -> dict[str, Any]:
        return {
            "metric_code": self.metric_code,
            "outcome": self.outcome.value,
            "methodology": self.methodology,
            "point_estimate": self.point_estimate,
            "standard_error": self.standard_error,
            "ci_lower_95": self.ci_lower_95,
            "ci_upper_95": self.ci_upper_95,
            "block_length": self.block_length,
            "block_length_rationale": self.block_length_rationale,
            "disclosures": self.disclosures,
        }


def estimate_dependence_block_length(series: Sequence[float], max_lag: int = 20) -> tuple[int, str]:
    """
    Determine block length from observed serial correlation structure.
    Methodological Description:
        Implements an internally deterministic dependence-informed block-length rule,
        constrained by an asymptotic N^(1/3) upper bound.
        The cited literature (Politis & White, 2004; Lahiri, 2003) provides the theoretical
        and asymptotic consistency basis (b -> inf, b/N -> 0 as N -> inf), rather than asserting
        that this engineering heuristic is the exact published optimal estimator.
        - Evaluates Bartlett's 95% white-noise significance threshold: 1.96 / sqrt(N).
        - Identifies the first lag q >= 1 where autocorrelation decays inside the Bartlett bounds.
        - If lag 1 is insignificant (|rho(1)| < 1.96 / sqrt(N)), no serial correlation is detected,
          so block length b = 1 (standard i.i.d. bootstrap).
        - If serial dependence exists up to lag q, blocks span the dependency horizon q.
        - Constrained by the asymptotic consistency upper bound b_max = floor(N^(1/3)).
        - Eliminates arbitrary hardcoded bounds (such as 5 or 22).
    """
    n = len(series)
    if n < 10:
        return 1, "Sample size too small for autocorrelation diagnostic (N < 10); defaulted to b=1."

    arr = np.asarray(series, dtype=np.float64)
    centered = arr - np.mean(arr)
    var = np.var(arr)
    if var <= 0 or math.isclose(var, 0.0, abs_tol=1e-15):
        return 1, "Zero sample variance; defaulted to b=1."

    cutoff = 1.96 / math.sqrt(n)
    effective_lag = 1
    max_search_lag = min(max_lag, max(1, n // 3))

    for lag in range(1, max_search_lag + 1):
        acf = float(np.sum(centered[:-lag] * centered[lag:]) / ((n - lag) * var))
        if abs(acf) < cutoff:
            effective_lag = lag
            break
        effective_lag = lag + 1

    # Theoretical consistency upper bound: floor(N^(1/3)) per Politis & White (2004)
    b_max = max(1, int(math.floor(n ** (1.0 / 3.0))))

    if effective_lag == 1:
        # Lag 1 is already inside Bartlett bounds -> no significant autocorrelation
        block_length = 1
        rationale = (
            f"Dependence diagnostic (N={n}): Lag-1 autocorrelation is below Bartlett 95% threshold ({cutoff:.4f}). "
            f"No significant serial dependence detected; dependence-informed rule selects b=1 (standard i.i.d. bootstrap)."
        )
    else:
        block_length = max(1, min(effective_lag, b_max))
        rationale = (
            f"Dependence diagnostic (N={n}): Autocorrelation decays below Bartlett 95% threshold ({cutoff:.4f}) at lag {effective_lag}. "
            f"Internally deterministic dependence-informed block-length rule, constrained by asymptotic upper bound floor(N^(1/3)) = {b_max}. Chosen block length: {block_length} days."
        )

    return block_length, rationale


def generate_circular_block_indices(n: int, block_length: int, rng: np.random.Generator) -> np.ndarray:
    """Generate circular block bootstrap indices of length n."""
    num_blocks = math.ceil(n / block_length)
    start_indices = rng.integers(0, n, size=num_blocks)
    indices = []
    for start in start_indices:
        for offset in range(block_length):
            indices.append((start + offset) % n)
            if len(indices) == n:
                break
        if len(indices) == n:
            break
    return np.array(indices, dtype=np.int64)


def evaluate_metric_uncertainty(
    metric_code: str,
    fund_returns: Sequence[float] | None = None,
    benchmark_returns: Sequence[float] | None = None,
    risk_free_rates: Sequence[float] | None = None,
    nav_series: Sequence[float] | None = None,
    iterations: int = 500,
    seed: int = 42,
) -> UncertaintyResult:
    """
    Execute metric-appropriate uncertainty evaluation.

    Prohibits applying standard block bootstrap to path-dependent or single point-to-point metrics.
    """
    # 1. Path-Dependent RSK-03: Max Drawdown
    if metric_code == "RSK-03":
        return UncertaintyResult(
            metric_code="RSK-03",
            outcome=EvidenceOutcome.N_A,
            methodology="INAPPLICABLE_FOR_PATH_DEPENDENT",
            point_estimate=None,
            disclosures=[
                "Path-dependent metric: Maximum drawdown is defined over continuous chronological price trajectories.",
                "The current circular block-bootstrap procedure is not applied to RSK-03 because naive return resampling can distort the path structure underlying maximum drawdown.",
                "Specialized path-preserving uncertainty methods are outside the current 2S-A scope.",
            ],
        )

    # 2. Point-to-Point Compounding RET-03: 3Y CAGR
    if metric_code == "RET-03":
        return UncertaintyResult(
            metric_code="RET-03",
            outcome=EvidenceOutcome.N_A,
            methodology="INAPPLICABLE_FOR_DISCRETE_ENDPOINT",
            point_estimate=None,
            disclosures=[
                "Point-to-point cumulative metric: CAGR estimand represents a fixed-period terminal wealth ratio.",
                "The current circular block-bootstrap procedure is not applied to RET-03 because bootstrap resampling changes observation multiplicities and therefore does not preserve the fixed-period terminal wealth ratio underlying the CAGR estimand.",
                "A separate uncertainty methodology would require explicit methodological justification and is outside the current 2S-A scope.",
            ],
        )

    # For distributional metrics, require valid fund_returns
    if fund_returns is None or len(fund_returns) < 30:
        return UncertaintyResult(
            metric_code=metric_code,
            outcome=EvidenceOutcome.INSUFFICIENT,
            methodology="INSUFFICIENT_OBSERVATIONS",
            disclosures=["Fewer than 30 return observations provided; bootstrap is statistically underpowered."],
        )

    n = len(fund_returns)
    block_length, rationale = estimate_dependence_block_length(fund_returns)
    rng = np.random.default_rng(seed)

    # 3. RSK-01: Annualized Volatility
    if metric_code == "RSK-01":
        point_est = ref_volatility(fund_returns)
        boot_estimates = []
        for _ in range(iterations):
            idx = generate_circular_block_indices(n, block_length, rng)
            sample_r = [fund_returns[i] for i in idx]
            boot_estimates.append(ref_volatility(sample_r))

        se = float(np.std(boot_estimates, ddof=1))
        ci_lower = float(np.percentile(boot_estimates, 2.5))
        ci_upper = float(np.percentile(boot_estimates, 97.5))

        return UncertaintyResult(
            metric_code="RSK-01",
            outcome=EvidenceOutcome.PASS,
            methodology="CIRCULAR_BLOCK_BOOTSTRAP",
            point_estimate=point_est,
            standard_error=se,
            ci_lower_95=ci_lower,
            ci_upper_95=ci_upper,
            block_length=block_length,
            block_length_rationale=rationale,
            disclosures=[
                f"Computed across {iterations} deterministic circular block bootstrap replications (seed={seed}).",
                "Non-parametric 95% percentile confidence interval [2.5%, 97.5%].",
            ],
        )

    # 4. RSK-06: Historical VaR 95%
    if metric_code == "RSK-06":
        point_est = ref_historical_var(fund_returns, confidence_level=0.95)
        boot_estimates = []
        for _ in range(iterations):
            idx = generate_circular_block_indices(n, block_length, rng)
            sample_r = [fund_returns[i] for i in idx]
            boot_estimates.append(ref_historical_var(sample_r, confidence_level=0.95))

        se = float(np.std(boot_estimates, ddof=1))
        ci_lower = float(np.percentile(boot_estimates, 2.5))
        ci_upper = float(np.percentile(boot_estimates, 97.5))

        return UncertaintyResult(
            metric_code="RSK-06",
            outcome=EvidenceOutcome.PASS,
            methodology="EMPIRICAL_QUANTILE_BLOCK_BOOTSTRAP",
            point_estimate=point_est,
            standard_error=se,
            ci_lower_95=ci_lower,
            ci_upper_95=ci_upper,
            block_length=block_length,
            block_length_rationale=rationale,
            disclosures=[
                f"Evaluated with {iterations} block bootstrap iterations.",
                "Quantifies empirical sample noise around the 5th percentile lower tail cutoff.",
            ],
        )

    # 5. RAT-01: Sharpe Ratio
    if metric_code == "RAT-01":
        rf = risk_free_rates if risk_free_rates is not None else 0.0
        point_est = ref_sharpe_ratio(fund_returns, rf)
        boot_estimates = []
        for _ in range(iterations):
            idx = generate_circular_block_indices(n, block_length, rng)
            sample_r = [fund_returns[i] for i in idx]
            sample_rf = [rf[i] for i in idx] if isinstance(rf, Sequence) else rf
            try:
                val = ref_sharpe_ratio(sample_r, sample_rf)
                if math.isfinite(val):
                    boot_estimates.append(val)
            except (ValueError, ZeroDivisionError):
                continue

        if len(boot_estimates) < iterations * 0.8:
            return UncertaintyResult(
                metric_code="RAT-01",
                outcome=EvidenceOutcome.CONDITIONAL,
                methodology="CIRCULAR_BLOCK_BOOTSTRAP",
                point_estimate=point_est,
                disclosures=["High frequency of degenerate resampled excess return variances."],
            )

        se = float(np.std(boot_estimates, ddof=1))
        ci_lower = float(np.percentile(boot_estimates, 2.5))
        ci_upper = float(np.percentile(boot_estimates, 97.5))

        return UncertaintyResult(
            metric_code="RAT-01",
            outcome=EvidenceOutcome.PASS,
            methodology="PAIRED_BLOCK_BOOTSTRAP",
            point_estimate=point_est,
            standard_error=se,
            ci_lower_95=ci_lower,
            ci_upper_95=ci_upper,
            block_length=block_length,
            block_length_rationale=rationale,
            disclosures=["Paired bootstrap preserves synchrony between portfolio return and daily risk-free yield curve."],
        )

    # 6. REL-01: Beta
    if metric_code == "REL-01":
        if benchmark_returns is None or len(benchmark_returns) != n:
            return UncertaintyResult(
                metric_code="REL-01",
                outcome=EvidenceOutcome.INSUFFICIENT,
                methodology="MISSING_BENCHMARK_SERIES",
                disclosures=["Synchronous benchmark return series required for Beta bootstrap."],
            )

        rf = risk_free_rates if risk_free_rates is not None else 0.0
        point_est = ref_beta(fund_returns, benchmark_returns, rf)
        boot_estimates = []
        for _ in range(iterations):
            idx = generate_circular_block_indices(n, block_length, rng)
            sample_p = [fund_returns[i] for i in idx]
            sample_b = [benchmark_returns[i] for i in idx]
            sample_rf = [rf[i] for i in idx] if isinstance(rf, Sequence) else rf
            try:
                val = ref_beta(sample_p, sample_b, sample_rf)
                if math.isfinite(val):
                    boot_estimates.append(val)
            except (ValueError, ZeroDivisionError):
                continue

        if len(boot_estimates) < iterations * 0.8:
            return UncertaintyResult(
                metric_code="REL-01",
                outcome=EvidenceOutcome.CONDITIONAL,
                methodology="PAIRED_BIVARIATE_BLOCK_BOOTSTRAP",
                point_estimate=point_est,
                disclosures=["Excessive collinear or degenerate bootstrap samples."],
            )

        se = float(np.std(boot_estimates, ddof=1))
        ci_lower = float(np.percentile(boot_estimates, 2.5))
        ci_upper = float(np.percentile(boot_estimates, 97.5))

        return UncertaintyResult(
            metric_code="REL-01",
            outcome=EvidenceOutcome.PASS,
            methodology="PAIRED_BIVARIATE_BLOCK_BOOTSTRAP",
            point_estimate=point_est,
            standard_error=se,
            ci_lower_95=ci_lower,
            ci_upper_95=ci_upper,
            block_length=block_length,
            block_length_rationale=rationale,
            disclosures=["Preserves cross-sectional dependency between fund and market benchmark observations."],
        )

    raise ValueError(f"Unsupported representative metric code: {metric_code}")
