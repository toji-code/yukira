from __future__ import annotations

import math
from typing import Optional


def period_return(
    start_value: float,
    end_value: float,
) -> float:
    """
    Calculate the simple period return.

    Formula:
        (end_value / start_value) - 1

    Returns a decimal:
        0.10 = 10%
        -0.05 = -5%
    """
    if not math.isfinite(start_value) or not math.isfinite(end_value):
        raise ValueError("start_value and end_value must be finite.")

    if start_value <= 0:
        raise ValueError("start_value must be greater than zero.")

    if end_value < 0:
        raise ValueError("end_value cannot be negative.")

    return (end_value / start_value) - 1.0


def cagr(
    start_value: float,
    end_value: float,
    years: float,
) -> float:
    """
    Calculate Compound Annual Growth Rate.

    Formula:
        (end_value / start_value) ** (1 / years) - 1

    Returns a decimal:
        0.12 = 12%

    `years` represents the actual elapsed period in years.
    """
    if not math.isfinite(start_value):
        raise ValueError("start_value must be finite.")

    if not math.isfinite(end_value):
        raise ValueError("end_value must be finite.")

    if not math.isfinite(years):
        raise ValueError("years must be finite.")

    if start_value <= 0:
        raise ValueError("start_value must be greater than zero.")

    if end_value <= 0:
        raise ValueError("end_value must be greater than zero.")

    if years <= 0:
        raise ValueError("years must be greater than zero.")

    return math.pow(end_value / start_value, 1.0 / years) - 1.0