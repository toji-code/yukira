"""
FBIL 91-Day Treasury Bill Risk-Free Rate Calculations (Phase 2N Approved / M2N-02).

Authoritative Specifications:
- Source: FBIL 91-Day Treasury Bill benchmark cutoff yield curve.
- Day-count convention: ACT/365 (FIMMDA / RBI money market standard).
- Daily conversion: R_{f,t} = y(t-1) * (Delta_d / 365.0)
- Preceding available observation lookback: up to 4 calendar days.
- Point-in-Time constraints: effective_date <= analysis_cutoff and availability_time <= knowledge_cutoff.
"""

from __future__ import annotations

import datetime
import math
from dataclasses import dataclass
from typing import Sequence


@dataclass(frozen=True)
class RiskFreeObservation:
    """Immutable bitemporal observation for risk-free benchmark yields."""
    effective_date: datetime.date
    quoted_yield: float  # Annualized decimal yield (e.g. 0.0695 for 6.95%)
    availability_time: datetime.datetime
    revision_seq: int = 1


def fbil_91d_tbill_to_daily_return(
    quoted_yield: float,
    elapsed_calendar_days: int | float = 1,
) -> float:
    """
    Convert an annualized FBIL 91-Day T-Bill yield to a periodic return using ACT/365 daycount.

    Formula:
        R_{f,t} = quoted_yield * (elapsed_calendar_days / 365.0)

    Parameters:
        quoted_yield: Annualized yield as decimal (e.g. 0.0700 for 7.00%).
        elapsed_calendar_days: Number of elapsed calendar days over holding period (1 for weekday, 3 for weekend).

    Returns:
        Periodic risk-free return as decimal.
    """
    if not isinstance(quoted_yield, (int, float)) or not math.isfinite(quoted_yield):
        raise ValueError("quoted_yield must be a finite numeric value.")

    if quoted_yield < 0:
        raise ValueError("quoted_yield cannot be negative.")

    if not isinstance(elapsed_calendar_days, (int, float)) or not math.isfinite(elapsed_calendar_days):
        raise ValueError("elapsed_calendar_days must be a finite numeric value.")

    if elapsed_calendar_days <= 0:
        raise ValueError("elapsed_calendar_days must be greater than zero.")

    return float(quoted_yield) * (float(elapsed_calendar_days) / 365.0)


def align_risk_free_series(
    observation_dates: Sequence[datetime.date | str],
    risk_free_observations: Sequence[RiskFreeObservation],
    analysis_cutoff: datetime.date | str,
    knowledge_cutoff: datetime.datetime | str,
    max_lookback_days: int = 4,
) -> list[float]:
    """
    Align daily risk-free returns to a sequence of chronological fund valuation dates.

    Enforces:
    1. Bitemporal PIT rules: effective_date <= analysis_cutoff and availability_time <= knowledge_cutoff.
    2. Highest revision sequence selection per effective date.
    3. Preceding available yield selection for each holding period [Date_{t-1}, Date_t].
    4. Multi-calendar-day interest accrual for weekends and holidays (ACT/365).
    5. Staleness rejection if no valid quote exists within max_lookback_days (4 calendar days).

    Parameters:
        observation_dates: Chronologically ordered NAV observation dates (length N >= 2).
        risk_free_observations: Available historical FBIL risk-free observations.
        analysis_cutoff: Historical calculation cutoff date.
        knowledge_cutoff: Point-in-time knowledge cutoff timestamp.
        max_lookback_days: Maximum allowable gap for preceding quote (default 4 days).

    Returns:
        List of N-1 aligned periodic risk-free returns corresponding to intervals between observation_dates.
    """
    if len(observation_dates) < 2:
        raise ValueError("At least two observation dates are required to compute periodic returns.")

    # Parse cutoffs
    if isinstance(analysis_cutoff, str):
        analysis_cutoff_date = datetime.date.fromisoformat(analysis_cutoff)
    else:
        analysis_cutoff_date = analysis_cutoff

    if isinstance(knowledge_cutoff, str):
        knowledge_cutoff_dt = datetime.datetime.fromisoformat(knowledge_cutoff)
    else:
        knowledge_cutoff_dt = knowledge_cutoff

    # Ensure timezone awareness consistency if knowledge_cutoff is aware
    has_tz = knowledge_cutoff_dt.tzinfo is not None

    # Parse and validate observation dates
    parsed_dates: list[datetime.date] = []
    for d in observation_dates:
        if isinstance(d, str):
            p_date = datetime.date.fromisoformat(d)
        elif isinstance(d, datetime.date):
            p_date = d
        else:
            raise TypeError("observation_dates elements must be date or ISO date string.")
        parsed_dates.append(p_date)

    # Check chronological ordering
    for i in range(1, len(parsed_dates)):
        if parsed_dates[i] <= parsed_dates[i - 1]:
            raise ValueError("observation_dates must be strictly chronologically increasing.")

    # Filter risk-free observations by PIT constraints and resolve latest revisions
    pit_rates: dict[datetime.date, tuple[int, float]] = {}  # date -> (revision_seq, quoted_yield)

    for obs in risk_free_observations:
        obs_avail = obs.availability_time
        if has_tz and obs_avail.tzinfo is None:
            # Assume UTC or local matching timezone
            obs_avail = obs_avail.replace(tzinfo=knowledge_cutoff_dt.tzinfo)
        elif not has_tz and obs_avail.tzinfo is not None:
            obs_avail = obs_avail.replace(tzinfo=None)

        if obs.effective_date <= analysis_cutoff_date and obs_avail <= knowledge_cutoff_dt:
            existing = pit_rates.get(obs.effective_date)
            if existing is None or obs.revision_seq > existing[0]:
                pit_rates[obs.effective_date] = (obs.revision_seq, obs.quoted_yield)

    available_dates = sorted(pit_rates.keys())
    if not available_dates:
        raise ValueError("No risk-free observations available prior to knowledge cutoff.")

    aligned_rf_returns: list[float] = []

    # For each periodic interval between parsed_dates[t-1] and parsed_dates[t]:
    # We require the risk-free rate effective at or immediately preceding parsed_dates[t-1].
    for t in range(1, len(parsed_dates)):
        start_date = parsed_dates[t - 1]
        end_date = parsed_dates[t]
        delta_days = (end_date - start_date).days

        # Find preceding valid rate on or before start_date
        # Binary search or scan backwards up to max_lookback_days
        resolved_yield: float | None = None
        for lookback in range(max_lookback_days + 1):
            candidate_date = start_date - datetime.timedelta(days=lookback)
            if candidate_date in pit_rates:
                resolved_yield = pit_rates[candidate_date][1]
                break

        if resolved_yield is None:
            raise ValueError(
                f"Missing or stale risk-free observation for date {start_date} "
                f"beyond {max_lookback_days}-calendar-day lookback limit."
            )

        rf_period = fbil_91d_tbill_to_daily_return(resolved_yield, delta_days)
        aligned_rf_returns.append(rf_period)

    return aligned_rf_returns
