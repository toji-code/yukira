"""Ulcer Index calculations."""

import math
from collections.abc import Sequence


def ulcer_index(values: Sequence[float]) -> float:
    """
    Calculate the Ulcer Index from a series of portfolio values.

    The Ulcer Index is the root mean square of percentage drawdowns
    from the historical running maximum.

    Args:
        values: Portfolio value observations in chronological order.

    Returns:
        Ulcer Index expressed as a positive percentage value.

    Raises:
        ValueError: If values are empty, contain non-positive or
            non-finite observations.
    """
    if len(values) == 0:
        raise ValueError("values must not be empty")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in values
    ):
        raise ValueError("values must contain only finite numeric values")

    if any(value <= 0 for value in values):
        raise ValueError("values must contain only positive values")

    running_max = values[0]
    squared_drawdowns = []

    for value in values:
        running_max = max(running_max, value)

        drawdown_percent = ((value / running_max) - 1.0) * 100.0
        squared_drawdowns.append(drawdown_percent ** 2)

    return float(math.sqrt(sum(squared_drawdowns) / len(squared_drawdowns)))