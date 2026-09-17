"""Historical Value at Risk calculations."""

import math
from collections.abc import Sequence


def historical_var(
    returns: Sequence[float],
    confidence_level: float = 0.95,
) -> float:
    """
    Calculate historical Value at Risk (VaR).

    For confidence level c:

        VaR_c = -quantile(returns, 1 - c)

    The result is expressed as a positive loss magnitude when the
    lower-tail quantile is negative.

    Linear interpolation is used for the empirical quantile.
    """
    if len(returns) == 0:
        raise ValueError("returns cannot be empty")

    if not isinstance(confidence_level, (int, float)):
        raise TypeError("confidence_level must be numeric")

    if not math.isfinite(confidence_level):
        raise ValueError("confidence_level must be finite")

    if not 0 < confidence_level < 1:
        raise ValueError("confidence_level must be between 0 and 1")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in returns
    ):
        raise ValueError("returns must be finite numeric values")

    sorted_returns = sorted(returns)

    percentile = 1.0 - confidence_level
    position = percentile * (len(sorted_returns) - 1)

    lower_index = math.floor(position)
    upper_index = math.ceil(position)

    if lower_index == upper_index:
        quantile = sorted_returns[lower_index]
    else:
        lower_value = sorted_returns[lower_index]
        upper_value = sorted_returns[upper_index]

        fraction = position - lower_index

        quantile = (
            lower_value
            + fraction * (upper_value - lower_value)
        )

    return float(-quantile)