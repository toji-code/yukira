"""Benchmark upside capture calculations."""

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


def upside_capture(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate upside capture ratio.

    Only periods where the benchmark return is positive are included.

    Formula:
        upside capture =
            portfolio upside return / benchmark upside return * 100

    A value above 100 means the portfolio gained more than the benchmark
    during benchmark-up periods.
    """
    _validate_returns(portfolio_returns, benchmark_returns)

    benchmark_up_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark > 0
    ]

    if not benchmark_up_periods:
        raise ValueError("no benchmark upside periods available")

    portfolio_upside = math.prod(
        1 + portfolio
        for portfolio, _ in benchmark_up_periods
    ) - 1

    benchmark_upside = math.prod(
        1 + benchmark
        for _, benchmark in benchmark_up_periods
    ) - 1

    if benchmark_upside == 0:
        raise ValueError("benchmark upside return cannot be zero")

    return float((portfolio_upside / benchmark_upside) * 100)