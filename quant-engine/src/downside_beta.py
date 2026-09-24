"""Downside beta calculations."""

import math
from collections.abc import Sequence


def downside_beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    min_downside_observations: int = 2,
) -> float:
    """
    Calculate portfolio downside beta conditioned on benchmark-down periods (Rb < 0).

    Formula:
        downside_beta = Cov(Rp, Rb | Rb < 0) / Var(Rb | Rb < 0)
                      = sum((Rp,t - Rp_bar_D)(Rb,t - Rb_bar_D)) / sum((Rb,t - Rb_bar_D)^2)

    Where:
        D = {t : Rb,t < 0}
        Only observations where benchmark return Rb < 0 are included.
        Rb = 0.0 and Rb > 0.0 are strictly EXCLUDED.
        Uses raw portfolio and raw benchmark returns (NO risk-free dependency; MKT-02 Risk-Free Dependency = FALSE).

    Sample covariance and sample variance (divisor |D| - 1) are used.
    Downside beta is dimensionless (no annualization multiplier).
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

    downside_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark < 0.0
    ]

    req_obs = max(2, min_downside_observations)
    if len(downside_periods) < req_obs:
        raise ValueError(
            f"at least {req_obs} benchmark downside observations are required (found {len(downside_periods)})"
        )

    portfolio_downside = [portfolio for portfolio, _ in downside_periods]
    benchmark_downside = [benchmark for _, benchmark in downside_periods]

    k = len(downside_periods)
    portfolio_mean = sum(portfolio_downside) / k
    benchmark_mean = sum(benchmark_downside) / k

    covariance = sum(
        (portfolio - portfolio_mean) * (benchmark - benchmark_mean)
        for portfolio, benchmark in downside_periods
    ) / (k - 1)

    benchmark_variance = sum(
        (benchmark - benchmark_mean) ** 2
        for benchmark in benchmark_downside
    ) / (k - 1)

    if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
        raise ValueError("benchmark downside variance must be greater than zero")

    return float(covariance / benchmark_variance)