"""Downside beta calculations."""

import math
from collections.abc import Sequence


def downside_beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate portfolio beta using only benchmark-down periods.

    Formula:
        downside_beta =
            covariance(portfolio, benchmark)
            / variance(benchmark)

    Only observations where benchmark return < 0 are included.

    Sample covariance and sample variance are used.
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
        if benchmark < 0
    ]

    if len(downside_periods) < 2:
        raise ValueError(
            "at least two benchmark downside observations are required"
        )

    portfolio_downside = [portfolio for portfolio, _ in downside_periods]
    benchmark_downside = [benchmark for _, benchmark in downside_periods]

    portfolio_mean = sum(portfolio_downside) / len(portfolio_downside)
    benchmark_mean = sum(benchmark_downside) / len(benchmark_downside)

    covariance = sum(
        (portfolio - portfolio_mean) * (benchmark - benchmark_mean)
        for portfolio, benchmark in downside_periods
    ) / (len(downside_periods) - 1)

    benchmark_variance = sum(
        (benchmark - benchmark_mean) ** 2
        for benchmark in benchmark_downside
    ) / (len(benchmark_downside) - 1)

    if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
        raise ValueError("benchmark downside variance must be greater than zero")

    return float(covariance / benchmark_variance)