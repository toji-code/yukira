"""Tracking error calculations."""

import math
from collections.abc import Sequence


def tracking_error(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    periods_per_year: int | float | None = None,
) -> float:
    """
    Calculate tracking error between portfolio and benchmark returns.

    Formula:

        active_return_i = portfolio_return_i - benchmark_return_i

        tracking_error =
            sample standard deviation(active_returns)

    If periods_per_year is supplied, the result is annualized:

        annualized_tracking_error =
            tracking_error * sqrt(periods_per_year)

    periods_per_year must be positive when supplied.
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) < 2:
        raise ValueError("at least two observations are required")

    if periods_per_year is not None:
        if not isinstance(periods_per_year, (int, float)):
            raise TypeError("periods_per_year must be numeric")

        if not math.isfinite(periods_per_year) or periods_per_year <= 0:
            raise ValueError(
                "periods_per_year must be finite and greater than zero"
            )

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

    active_returns = [
        portfolio - benchmark
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
    ]

    mean_active_return = sum(active_returns) / len(active_returns)

    variance = sum(
        (active_return - mean_active_return) ** 2
        for active_return in active_returns
    ) / (len(active_returns) - 1)

    result = math.sqrt(variance)

    if periods_per_year is not None:
        result *= math.sqrt(periods_per_year)

    return float(result)