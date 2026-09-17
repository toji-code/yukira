"""Benchmark upside and downside capture calculations."""

import math
from collections.abc import Sequence


def _validate_returns(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> None:
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


def downside_capture(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate downside capture ratio.

    Only periods where the benchmark return is negative are included.

    Formula:
        downside capture =
            portfolio downside return / benchmark downside return * 100

    A value below 100 means the portfolio lost less than the benchmark
    during benchmark-down periods.
    """
    _validate_returns(portfolio_returns, benchmark_returns)

    benchmark_down_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark < 0
    ]

    if not benchmark_down_periods:
        raise ValueError("no benchmark downside periods available")

    portfolio_downside = math.prod(
        1 + portfolio
        for portfolio, _ in benchmark_down_periods
    ) - 1

    benchmark_downside = math.prod(
        1 + benchmark
        for _, benchmark in benchmark_down_periods
    ) - 1

    if benchmark_downside == 0:
        raise ValueError("benchmark downside return cannot be zero")

    return float((portfolio_downside / benchmark_downside) * 100)