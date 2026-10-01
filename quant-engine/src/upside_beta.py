"""Upside beta calculations."""

import math
from collections.abc import Sequence


def upside_beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    min_upside_observations: int = 150,
) -> float:
    """
    Calculate portfolio upside beta conditioned on benchmark-up periods (Rb > 0).

    Formula:
        upside_beta = Cov(Rp, Rb | Rb > 0) / Var(Rb | Rb > 0)
                     = sum((Rp,t - Rp_bar_U)(Rb,t - Rb_bar_U)) / sum((Rb,t - Rb_bar_U)^2)

    Where:
        U = {t : Rb,t > 0}
        Only observations where benchmark return Rb > 0 are included.
        Rb = 0.0 and Rb < 0.0 are strictly EXCLUDED.
        Uses raw portfolio and raw benchmark returns (NO risk-free dependency; MKT-02 Risk-Free Dependency = FALSE).

    Sample covariance and sample variance (divisor |U| - 1) are used.
    Upside beta is dimensionless (no annualization multiplier).
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) == 0:
        raise ValueError("return series cannot be empty")

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

    upside_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark > 0.0
    ]

    if min_upside_observations < 150:
        raise ValueError("min_upside_observations must be at least 150")
    req_obs = min_upside_observations
    if len(upside_periods) < req_obs:
        raise ValueError(
            f"at least {req_obs} benchmark upside observations are required (found {len(upside_periods)})"
        )

    portfolio_upside = [portfolio for portfolio, _ in upside_periods]
    benchmark_upside = [benchmark for _, benchmark in upside_periods]

    k = len(upside_periods)
    portfolio_mean = sum(portfolio_upside) / k
    benchmark_mean = sum(benchmark_upside) / k

    covariance = sum(
        (portfolio - portfolio_mean) * (benchmark - benchmark_mean)
        for portfolio, benchmark in upside_periods
    ) / (k - 1)

    benchmark_variance = sum(
        (benchmark - benchmark_mean) ** 2
        for benchmark in benchmark_upside
    ) / (k - 1)

    if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
        raise ValueError("benchmark upside variance must be greater than zero")

    return float(covariance / benchmark_variance)