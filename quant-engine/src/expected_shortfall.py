"""Historical Expected Shortfall (CVaR) calculations."""

import math
from collections.abc import Sequence


def historical_expected_shortfall(
    returns: Sequence[float],
    confidence_level: float = 0.95,
) -> float:
    """
    Calculate historical Expected Shortfall (ES).

    ES is the negative mean return of observations at or below
    the lower-tail historical quantile.

    Args:
        returns: Periodic portfolio returns.
        confidence_level: Confidence level between 0 and 1.

    Returns:
        Expected shortfall expressed as a signed loss measure.

    Raises:
        ValueError: If returns are empty, contain invalid values,
            confidence level is invalid, or the tail contains no observations.
        TypeError: If confidence_level is not numeric.
    """
    if len(returns) == 0:
        raise ValueError("returns must not be empty")

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
        raise ValueError("returns must contain only finite numeric values")

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
        quantile = lower_value + fraction * (upper_value - lower_value)

    tail_returns = [
        value for value in sorted_returns
        if value <= quantile
    ]

    if not tail_returns:
        raise ValueError("historical tail contains no observations")

    return float(-sum(tail_returns) / len(tail_returns))