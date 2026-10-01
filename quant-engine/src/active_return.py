"""Active return calculations (benchmark-relative excess return)."""

import math
from collections.abc import Sequence


def mean_active_return(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate the arithmetic mean of the paired active return series.

    Formula:

        active_return_i = portfolio_return_i - benchmark_return_i

        mean_active_return = sum(active_return_i) / N

    The divisor is N (the full paired observation count). This is the
    established YUKIRA active-return numerator used by the Information Ratio
    (MKT-02 / RAT-04) and reported as the MKT-01 diagnostic
    `annualized_mean_active_return`.

    Active return is a raw excess over the benchmark. It has no risk-free
    dependency and is dimensionless-per-period until annualized.
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) == 0:
        raise ValueError("at least one paired return observation is required")

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

    return float(sum(active_returns) / len(active_returns))


def annualized_mean_active_return(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    periods_per_year: int | float,
) -> float:
    """
    Calculate the annualized mean active return over the primary benchmark.

    Formula:

        annualized_active_return =
            mean(active_return_i) * periods_per_year

    The annualization is a LINEAR multiplier on the mean active return. It is
    deliberately not the sqrt(periods_per_year) dispersion scaling used by
    tracking error: active return is a level (a mean), not a dispersion.

    An active return of exactly 0.0 (fund return identical to benchmark
    return) is a valid, meaningful result. Unlike the Information Ratio,
    this metric has no denominator and therefore never divides by zero.

    periods_per_year must be explicitly supplied because the return
    frequency is a methodological choice that should not be assumed.
    """
    if not isinstance(periods_per_year, (int, float)):
        raise TypeError("periods_per_year must be numeric")

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year must be finite and greater than zero")

    mean_active = mean_active_return(portfolio_returns, benchmark_returns)

    return float(mean_active * periods_per_year)
