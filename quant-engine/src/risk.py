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


def drawdown_details(
    values: Sequence[float],
    dates: Sequence[str] | None = None,
) -> dict:
    """
    Calculate maximum drawdown and its peak/trough details.
    """
    _validate_series(values)
    drawdowns = drawdown_series(values)
    max_dd = min(drawdowns)
    trough_idx = drawdowns.index(max_dd)

    # Peak before or at trough
    running_peak = values[0]
    peak_idx = 0
    for i in range(trough_idx + 1):
        if values[i] >= running_peak:
            running_peak = values[i]
            peak_idx = i

    return {
        "max_drawdown": max_dd,
        "peak_nav": float(running_peak),
        "trough_nav": float(values[trough_idx]),
        "peak_index": peak_idx,
        "trough_index": trough_idx,
        "peak_date": dates[peak_idx] if (dates and peak_idx < len(dates)) else None,
        "trough_date": dates[trough_idx] if (dates and trough_idx < len(dates)) else None,
    }


def maximum_drawdown_duration(
    values: Sequence[float],
    dates: Sequence[str] | None = None,
) -> dict:
    """
    Calculate the maximum drawdown duration in calendar days and trading periods.
    Evaluates each peak-to-recovery episode (or peak-to-cutoff for ongoing drawdowns).
    """
    import datetime

    _validate_series(values)
    if len(values) < 2:
        return {
            "max_duration_calendar_days": 0,
            "max_duration_trading_days": 0,
            "is_ongoing_at_cutoff": False,
            "worst_episode_peak_date": dates[0] if dates else None,
            "worst_episode_recovery_date": dates[0] if dates else None,
            "worst_episode_trough_date": dates[0] if dates else None,
            "worst_episode_trough_nav": float(values[0]) if len(values) > 0 else 0.0,
        }

    parsed_dates = None
    if dates is not None:
        if len(dates) != len(values):
            raise ValueError("dates and values must have the same length")
        parsed_dates = []
        for d in dates:
            if isinstance(d, datetime.date):
                parsed_dates.append(d)
            elif isinstance(d, str):
                parsed_dates.append(datetime.date.fromisoformat(d))
            else:
                raise TypeError("date items must be str or datetime.date")

    running_peak = values[0]
    peak_idx = 0
    episodes = []

    for t in range(1, len(values)):
        val = values[t]
        if val >= running_peak:
            # If we were underwater, this observation closes the episode
            if peak_idx < t - 1 or values[t - 1] < running_peak:
                c_days = (parsed_dates[t] - parsed_dates[peak_idx]).days if parsed_dates else (t - peak_idx)
                t_days = t - peak_idx
                trough_idx = peak_idx
                min_v = running_peak
                for k in range(peak_idx, t + 1):
                    if values[k] < min_v:
                        min_v = values[k]
                        trough_idx = k
                episodes.append({
                    "peak_idx": peak_idx,
                    "recovery_idx": t,
                    "trough_idx": trough_idx,
                    "is_ongoing": False,
                    "duration_calendar_days": c_days,
                    "duration_trading_days": t_days,
                })
            running_peak = val
            peak_idx = t

    # Check if ongoing drawdown at the cutoff date
    if peak_idx < len(values) - 1:
        c_days = (parsed_dates[-1] - parsed_dates[peak_idx]).days if parsed_dates else (len(values) - 1 - peak_idx)
        t_days = len(values) - 1 - peak_idx
        trough_idx = peak_idx
        min_v = running_peak
        for k in range(peak_idx, len(values)):
            if values[k] < min_v:
                min_v = values[k]
                trough_idx = k
        episodes.append({
            "peak_idx": peak_idx,
            "recovery_idx": None,
            "trough_idx": trough_idx,
            "is_ongoing": True,
            "duration_calendar_days": c_days,
            "duration_trading_days": t_days,
        })

    if not episodes:
        return {
            "max_duration_calendar_days": 0,
            "max_duration_trading_days": 0,
            "is_ongoing_at_cutoff": False,
            "worst_episode_peak_date": str(parsed_dates[0]) if parsed_dates else None,
            "worst_episode_recovery_date": str(parsed_dates[-1]) if parsed_dates else None,
            "worst_episode_trough_date": str(parsed_dates[0]) if parsed_dates else None,
            "worst_episode_trough_nav": float(values[0]),
        }

    worst_ep = max(episodes, key=lambda e: (e["duration_calendar_days"], e["duration_trading_days"]))
    return {
        "max_duration_calendar_days": int(worst_ep["duration_calendar_days"]),
        "max_duration_trading_days": int(worst_ep["duration_trading_days"]),
        "is_ongoing_at_cutoff": bool(worst_ep["is_ongoing"]),
        "worst_episode_peak_date": str(parsed_dates[worst_ep["peak_idx"]]) if parsed_dates else None,
        "worst_episode_recovery_date": str(parsed_dates[worst_ep["recovery_idx"]]) if (parsed_dates and worst_ep["recovery_idx"] is not None) else None,
        "worst_episode_trough_date": str(parsed_dates[worst_ep["trough_idx"]]) if parsed_dates else None,
        "worst_episode_trough_nav": float(values[worst_ep["trough_idx"]]),
    }