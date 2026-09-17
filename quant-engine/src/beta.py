"""Portfolio beta calculations."""

import math
from collections.abc import Sequence


def beta(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate portfolio beta relative to a benchmark.

    Formula:
        beta = covariance(portfolio, benchmark)
               / variance(benchmark)

    Uses sample covariance and sample variance. The resulting beta
    represents the portfolio's sensitivity to benchmark movements.
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

    portfolio_mean = sum(portfolio_returns) / len(portfolio_returns)
    benchmark_mean = sum(benchmark_returns) / len(benchmark_returns)

    covariance = sum(
        (portfolio - portfolio_mean) * (benchmark - benchmark_mean)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
    ) / (len(portfolio_returns) - 1)

    benchmark_variance = sum(
        (benchmark - benchmark_mean) ** 2
        for benchmark in benchmark_returns
    ) / (len(benchmark_returns) - 1)

    if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
        raise ValueError("benchmark variance must be greater than zero")

    return float(covariance / benchmark_variance)