from __future__ import annotations

import math
from typing import Sequence


def _validate_series(values: Sequence[float]) -> None:
    if len(values) == 0:
        raise ValueError("values must contain at least one observation.")

    for value in values:
        if not math.isfinite(value):
            raise ValueError("values must contain only finite numbers.")

        if value < 0:
            raise ValueError("values cannot contain negative values.")


def drawdown_series(values: Sequence[float]) -> list[float]:
    """
    Calculate drawdown from the running peak.

    Returns decimal values:
        0.00  = no drawdown
       -0.10  = 10% drawdown
    """
    _validate_series(values)

    running_peak = values[0]
    drawdowns: list[float] = []

    for value in values:
        running_peak = max(running_peak, value)

        if running_peak == 0:
            drawdowns.append(0.0)
        else:
            drawdowns.append((value / running_peak) - 1.0)

    return drawdowns


def maximum_drawdown(values: Sequence[float]) -> float:
    """
    Calculate the maximum drawdown of a value series.

    Returns a decimal:
        0.00  = no drawdown
       -0.25  = 25% maximum drawdown
    """
    drawdowns = drawdown_series(values)

    return min(drawdowns)