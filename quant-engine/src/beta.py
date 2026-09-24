"""Portfolio beta calculations."""

import math
from collections.abc import Sequence


def beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    risk_free_rates: Sequence[float] | float | None = None,
) -> float:
    """
    Calculate portfolio beta relative to a benchmark using Ordinary Least Squares (OLS) regression with intercept.

    Formula:
        (Rp,t - Rf,t) = alpha + beta * (Rb,t - Rf,t) + epsilon_t

        beta = Cov(Rp - Rf, Rb - Rf) / Var(Rb - Rf)
             = sum((x_t - x_bar)(y_t - y_bar)) / sum((x_t - x_bar)^2)

    Where:
        y_t = Rp,t - Rf,t (portfolio excess return)
        x_t = Rb,t - Rf,t (benchmark excess return)
        Rf,t is the daily risk-free rate from M2N-02 (or 0.0 / None if raw returns).

    Beta is dimensionless and scale-invariant (no annualization multiplier).
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) < 2:
        raise ValueError("at least two observations are required")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in portfolio_returns
    ):
        raise ValueError("portfolio returns must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in benchmark_returns
    ):
        raise ValueError("benchmark returns must be finite numeric values")

    if risk_free_rates is None:
        y = list(portfolio_returns)
        x = list(benchmark_returns)
    elif isinstance(risk_free_rates, Sequence) and not isinstance(risk_free_rates, (str, bytes)):
        if len(risk_free_rates) != len(portfolio_returns):
            raise ValueError("portfolio returns and risk-free rates must have equal length")
        if any(
            not isinstance(value, (int, float)) or not math.isfinite(value)
            for value in risk_free_rates
        ):
            raise ValueError("risk-free rates must be finite numeric values")
        y = [p - rf for p, rf in zip(portfolio_returns, risk_free_rates)]
        x = [b - rf for b, rf in zip(benchmark_returns, risk_free_rates)]
    elif isinstance(risk_free_rates, (int, float)):
        if not math.isfinite(risk_free_rates):
            raise ValueError("risk-free rate must be a finite numeric value")
        y = [p - risk_free_rates for p in portfolio_returns]
        x = [b - risk_free_rates for b in benchmark_returns]
    else:
        raise TypeError("risk_free_rates must be None, numeric, or a Sequence of numeric values")

    n = len(x)
    x_mean = sum(x) / n
    y_mean = sum(y) / n

    covariance = sum((x_i - x_mean) * (y_i - y_mean) for x_i, y_i in zip(x, y)) / (n - 1)
    benchmark_variance = sum((x_i - x_mean) ** 2 for x_i in x) / (n - 1)

    if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
        raise ValueError("benchmark variance must be greater than zero")

    return float(covariance / benchmark_variance)