"""Portfolio-relative performance calculations."""

from collections.abc import Sequence

import math


def relative_returns(
    portfolio_values: Sequence[float],
    benchmark_values: Sequence[float],
) -> list[float]:
    """
    Calculate period-by-period portfolio returns relative to a benchmark.

    Each observation is:

        (portfolio_return) - (benchmark_return)

    Returns one fewer observation than the input series.
    """
    if len(portfolio_values) != len(benchmark_values):
        raise ValueError("portfolio and benchmark series must have equal length")

    if len(portfolio_values) < 2:
        raise ValueError("at least two observations are required")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in portfolio_values
    ):
        raise ValueError("portfolio values must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in benchmark_values
    ):
        raise ValueError("benchmark values must be finite numeric values")

    if any(value <= 0 for value in portfolio_values):
        raise ValueError("portfolio values must be strictly positive")

    if any(value <= 0 for value in benchmark_values):
        raise ValueError("benchmark values must be strictly positive")

    result = []

    for index in range(1, len(portfolio_values)):
        portfolio_return = (
            portfolio_values[index] / portfolio_values[index - 1]
        ) - 1

        benchmark_return = (
            benchmark_values[index] / benchmark_values[index - 1]
        ) - 1

        result.append(portfolio_return - benchmark_return)

    return result