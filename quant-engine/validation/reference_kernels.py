"""Independent mathematical reference implementations for Phase 2S-A.

CRITICAL ARCHITECTURAL GUARDRAIL:
These reference kernels MUST be genuinely independent of production functions in src/.
They must never simply call production functions and label the result "reference".
They implement the financial formulas from independent mathematical derivations
to detect shared implementation defects.
"""

from __future__ import annotations

import math
from typing import Sequence
import numpy as np


def ref_cagr(
    start_value: float,
    end_value: float,
    elapsed_calendar_days: int | float,
) -> float:
    """
    Independent reference calculation for Compound Annual Growth Rate (RET-03).
    Formula: (end_value / start_value) ** (365.25 / elapsed_calendar_days) - 1.0
    """
    if not math.isfinite(start_value) or start_value <= 0:
        raise ValueError("start_value must be finite and positive")
    if not math.isfinite(end_value) or end_value <= 0:
        raise ValueError("end_value must be finite and positive")
    if not math.isfinite(elapsed_calendar_days) or elapsed_calendar_days <= 0:
        raise ValueError("elapsed_calendar_days must be finite and positive")

    annualization_factor = 365.25 / float(elapsed_calendar_days)
    growth_multiple = float(end_value) / float(start_value)
    return math.pow(growth_multiple, annualization_factor) - 1.0


def ref_simple_returns(nav_series: Sequence[float]) -> list[float]:
    """
    Calculate discrete simple periodic returns: R_t = (NAV_t / NAV_{t-1}) - 1.0.
    Authoritative convention per Phase 2H Line 431 and production contract.
    Logarithmic returns ln(NAV_t / NAV_{t-1}) are strictly rejected as non-compliant.
    """
    if len(nav_series) < 2:
        raise ValueError("At least two observations required for returns")
    for v in nav_series:
        if not math.isfinite(v) or v <= 0:
            raise ValueError("NAV observations must be finite and positive")
    return [(curr / prev) - 1.0 for prev, curr in zip(nav_series[:-1], nav_series[1:])]


def ref_volatility_from_nav(
    nav_series: Sequence[float],
    periods_per_year: float = 252.0,
) -> float:
    """
    Independent reference calculation for Annualized Volatility (RSK-01) from NAV series.
    Computes daily simple returns and annualizes sample standard deviation (ddof=1) by sqrt(252).
    """
    returns = ref_simple_returns(nav_series)
    return ref_volatility(returns, periods_per_year=periods_per_year)


def ref_volatility(
    returns: Sequence[float],
    periods_per_year: float = 252.0,
) -> float:
    """
    Independent reference calculation for Annualized Volatility (RSK-01).
    Operates on discrete simple returns R_t = (NAV_t / NAV_{t-1}) - 1 per Phase 2H.
    Uses a two-pass sample standard deviation algorithm with N-1 divisor.
    Formula: sqrt(sum((r - mean)^2) / (N - 1)) * sqrt(periods_per_year)
    """
    if len(returns) < 2:
        raise ValueError("At least two return observations required for sample volatility")
    for r in returns:
        if not math.isfinite(r):
            raise ValueError("All return values must be finite")
    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year must be finite and positive")

    n = len(returns)
    mean_return = sum(returns) / n
    sum_squared_deviations = sum((r - mean_return) ** 2 for r in returns)
    sample_variance = sum_squared_deviations / (n - 1)
    sample_std = math.sqrt(sample_variance)
    return sample_std * math.sqrt(periods_per_year)


def ref_maximum_drawdown(
    nav_series: Sequence[float],
) -> float:
    """
    Independent reference calculation for Maximum Drawdown (RSK-03).
    Formula: min_t (P_t / max_{s <= t}(P_s) - 1.0)
    Returns a signed non-positive decimal (e.g. -0.1288).
    """
    if len(nav_series) < 1:
        raise ValueError("At least one observation required for maximum drawdown")
    for v in nav_series:
        if not math.isfinite(v) or v < 0:
            raise ValueError("NAV observations must be finite and non-negative")

    running_peak = nav_series[0]
    max_dd = 0.0

    for val in nav_series:
        if val > running_peak:
            running_peak = val
        if running_peak > 0:
            dd = (val / running_peak) - 1.0
            if dd < max_dd:
                max_dd = dd

    return float(max_dd)


def ref_historical_var(
    returns: Sequence[float],
    confidence_level: float = 0.95,
) -> float:
    """
    Independent reference calculation for Historical Value at Risk (RSK-06).
    Formula: VaR_c = -quantile(returns, 1 - c)
    Expressed as a positive loss magnitude using numpy.percentile linear interpolation.
    """
    if len(returns) < 2:
        raise ValueError("At least two observations required for empirical VaR")
    for r in returns:
        if not math.isfinite(r):
            raise ValueError("All return observations must be finite")
    if not (0.0 < confidence_level < 1.0):
        raise ValueError("confidence_level must be strictly between 0 and 1")

    alpha = (1.0 - confidence_level) * 100.0
    arr = np.asarray(returns, dtype=np.float64)
    q = np.percentile(arr, alpha, method="linear")
    return float(-q)


def ref_sharpe_ratio(
    returns: Sequence[float],
    risk_free_rates: Sequence[float] | float,
    periods_per_year: float = 252.0,
) -> float:
    """
    Independent reference calculation for Annualized Sharpe Ratio (RAT-01).
    Approved Methodology (Phase 2N M2N-01 / M2N-05 & Phase 2O):
        Numerator: Annualized arithmetic mean daily excess return = mean(R_{p,t} - R_{f,t}) * periods_per_year.
        Denominator: Annualized standard deviation of daily excess return = stdev(R_{p,t} - R_{f,t}, ddof=1) * sqrt(periods_per_year).
    Ratio:
        Sharpe = (mean(excess) * periods_per_year) / (stdev(excess) * sqrt(periods_per_year))
               = (mean(excess) / stdev(excess)) * sqrt(periods_per_year)
    Supports both scalar risk-free rate and synchronized FBIL daily risk-free sequences.
    """
    if len(returns) < 2:
        raise ValueError("At least two observations required for Sharpe ratio")
    for r in returns:
        if not math.isfinite(r):
            raise ValueError("Return observations must be finite")

    n = len(returns)
    if isinstance(risk_free_rates, (int, float)):
        if not math.isfinite(risk_free_rates):
            raise ValueError("risk_free_rate scalar must be finite")
        excess = [r - float(risk_free_rates) for r in returns]
    elif isinstance(risk_free_rates, Sequence):
        if len(risk_free_rates) != n:
            raise ValueError("risk_free_rates sequence length must match returns")
        for rf in risk_free_rates:
            if not math.isfinite(rf):
                raise ValueError("risk_free_rates elements must be finite")
        excess = [r - rf for r, rf in zip(returns, risk_free_rates)]
    else:
        raise TypeError("risk_free_rates must be a float or Sequence of floats")

    mean_excess = sum(excess) / n
    sum_sq_diff = sum((x - mean_excess) ** 2 for x in excess)
    sample_var = sum_sq_diff / (n - 1)

    if sample_var <= 0.0 or math.isclose(sample_var, 0.0, abs_tol=1e-15):
        raise ValueError("Excess return variance is zero; Sharpe ratio is undefined")

    sample_std = math.sqrt(sample_var)
    return float((mean_excess / sample_std) * math.sqrt(periods_per_year))


def ref_beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    risk_free_rates: Sequence[float] | float | None = None,
) -> float:
    """
    Independent reference calculation for Beta (REL-01) using OLS covariance over benchmark variance.
    Formula: Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("Portfolio and benchmark returns must have identical lengths")
    if len(portfolio_returns) < 2:
        raise ValueError("At least two observations required for beta")

    n = len(portfolio_returns)
    for p, b in zip(portfolio_returns, benchmark_returns):
        if not math.isfinite(p) or not math.isfinite(b):
            raise ValueError("All return values must be finite")

    if risk_free_rates is None:
        y = list(portfolio_returns)
        x = list(benchmark_returns)
    elif isinstance(risk_free_rates, (int, float)):
        y = [p - float(risk_free_rates) for p in portfolio_returns]
        x = [b - float(risk_free_rates) for b in benchmark_returns]
    elif isinstance(risk_free_rates, Sequence):
        if len(risk_free_rates) != n:
            raise ValueError("risk_free_rates sequence length must match returns")
        y = [p - rf for p, rf in zip(portfolio_returns, risk_free_rates)]
        x = [b - rf for b, rf in zip(benchmark_returns, risk_free_rates)]
    else:
        raise TypeError("risk_free_rates must be None, float, or Sequence of floats")

    mean_x = sum(x) / n
    mean_y = sum(y) / n

    cov = sum((xi - mean_x) * (yi - mean_y) for xi, yi in zip(x, y)) / (n - 1)
    var_x = sum((xi - mean_x) ** 2 for xi in x) / (n - 1)

    if var_x <= 0.0 or math.isclose(var_x, 0.0, abs_tol=1e-15):
        raise ValueError("Benchmark variance is zero; Beta is undefined")

    return float(cov / var_x)
