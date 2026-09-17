from __future__ import annotations

import math
from statistics import stdev
from typing import Sequence


def _validate_values(values: Sequence[float]) -> None:
    if len(values) == 0:
        raise ValueError("values must contain at least one observation.")

    for value in values:
        if not math.isfinite(value):
            raise ValueError("values must contain only finite numbers.")


def periodic_returns(values: Sequence[float]) -> list[float]:
    """
    Calculate simple returns between consecutive observations.

    Formula:
        return_t = (value_t / value_(t-1)) - 1

    Example:
        [100, 110, 99]
        -> [0.10, -0.10]

    The input values must be strictly positive because they
    represent a value series such as NAV or an index level.
    """
    _validate_values(values)

    if len(values) < 2:
        raise ValueError(
            "At least two observations are required to calculate returns."
        )

    for value in values:
        if value <= 0:
            raise ValueError(
                "All values must be greater than zero."
            )

    return [
        (current / previous) - 1.0
        for previous, current in zip(values[:-1], values[1:])
    ]


def volatility(
    returns: Sequence[float],
    periods_per_year: float,
) -> float:
    """
    Calculate annualized sample volatility.

    Formula:
        sample_standard_deviation(returns)
        * sqrt(periods_per_year)

    Returns a decimal:
        0.15 = 15% annualized volatility.

    `periods_per_year` must be explicitly supplied by the caller.
    """
    _validate_values(returns)

    if len(returns) < 2:
        raise ValueError(
            "At least two return observations are required."
        )

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError(
            "periods_per_year must be finite and greater than zero."
        )

    return stdev(returns) * math.sqrt(periods_per_year)


def downside_deviation(
    returns: Sequence[float],
    target_return: float,
    periods_per_year: float,
) -> float:
    """
    Calculate annualized downside deviation.

    Only returns below `target_return` contribute to downside risk.

    Formula:
        sqrt(
            mean(
                min(return - target_return, 0)^2
            )
        ) * sqrt(periods_per_year)

    Returns a decimal:
        0.10 = 10% annualized downside deviation.

    The denominator uses all observations, not only the
    observations below the target.
    """
    _validate_values(returns)

    if len(returns) == 0:
        raise ValueError(
            "At least one return observation is required."
        )

    if not math.isfinite(target_return):
        raise ValueError(
            "target_return must be finite."
        )

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError(
            "periods_per_year must be finite and greater than zero."
        )

    squared_downside = [
        min(return_value - target_return, 0.0) ** 2
        for return_value in returns
    ]

    mean_squared_downside = sum(squared_downside) / len(squared_downside)

    return math.sqrt(mean_squared_downside) * math.sqrt(periods_per_year)