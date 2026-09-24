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


def cagr_from_calendar_days(
    start_value: float,
    end_value: float,
    elapsed_calendar_days: int | float,
) -> float:
    """
    Calculate Compound Annual Growth Rate using the approved Julian 365.25 calendar-day convention.

    Formula:
        (end_value / start_value) ** (365.25 / elapsed_calendar_days) - 1.0

    Returns a decimal:
        0.12 = 12%

    `elapsed_calendar_days` represents the exact number of elapsed calendar days.
    """
    if not math.isfinite(start_value) or start_value <= 0:
        raise ValueError("start_value must be finite and greater than zero.")

    if not math.isfinite(end_value) or end_value <= 0:
        raise ValueError("end_value must be finite and greater than zero.")

    if not math.isfinite(elapsed_calendar_days) or elapsed_calendar_days <= 0:
        raise ValueError("elapsed_calendar_days must be finite and greater than zero.")

    return math.pow(end_value / start_value, 365.25 / float(elapsed_calendar_days)) - 1.0