"""Information Ratio calculations."""

import math
from collections.abc import Sequence


def information_ratio(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    periods_per_year: int | float,
) -> float:
    """
    Calculate the annualized Information Ratio.

    Formula:

        active_return_i = portfolio_return_i - benchmark_return_i

        tracking_error = sample standard deviation(active_returns)

        IR = mean(active_returns) / tracking_error
             * sqrt(periods_per_year)

    Sample standard deviation is used for tracking error.

    periods_per_year must be explicitly supplied because the return
    frequency is a methodological choice that should not be assumed.
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) < 2:
        raise ValueError("at least two observations are required")

    if not isinstance(periods_per_year, (int, float)):
        raise TypeError("periods_per_year must be numeric")

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year must be finite and greater than zero")

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

    active_mean = mean_active_return

    variance = sum(
        (active_return - active_mean) ** 2
        for active_return in active_returns
    ) / (len(active_returns) - 1)

    tracking_error = math.sqrt(variance)

    if math.isclose(tracking_error, 0.0, abs_tol=1e-15):
        raise ValueError("tracking error must be greater than zero")

    return float(
        (mean_active_return / tracking_error)
        * math.sqrt(periods_per_year)
    )