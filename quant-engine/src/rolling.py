from __future__ import annotations

import math
from typing import Sequence


def rolling_returns(
    values: Sequence[float],
    window: int,
) -> list[float]:
    """
    Calculate simple compounded rolling returns.

    For each window:

        return = (ending_value / starting_value) - 1

    Example:
        values = [100, 110, 121]
        window = 3

        result = [0.21]

    `window` represents the number of observations, not years.
    """
    if len(values) == 0:
        raise ValueError("values must contain at least one observation.")

    if window < 2:
        raise ValueError("window must be at least 2.")

    if window > len(values):
        raise ValueError("window cannot exceed the number of observations.")

    for value in values:
        if not math.isfinite(value):
            raise ValueError("values must contain only finite numbers.")

        if value <= 0:
            raise ValueError("values must be greater than zero.")

    return [
        (values[index + window - 1] / values[index]) - 1.0
        for index in range(len(values) - window + 1)
    ]