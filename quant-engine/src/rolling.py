from __future__ import annotations

import datetime
import math
from typing import Any, Sequence


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


def compute_rolling_cagrs(
    dates: Sequence[str | datetime.date],
    values: Sequence[float],
    window_years: int = 3,
    max_lookback_days: int = 4,
) -> list[dict[str, Any]]:
    """
    Calculate annualized rolling CAGRs for a historical time series.
    Implements Phase 2H methodology §RET-05 / §RET-06.

    For each evaluation date T_end:
        1. Target start date T_start_target = T_end - window_years.
        2. Resolve authoritative start date within candidate [0, max_lookback_days] window.
        3. Annualized CAGR = (V_end / V_start) ** (365.25 / elapsed_calendar_days) - 1.0

    Returns list of dicts:
        {"start_date": "YYYY-MM-DD", "end_date": "YYYY-MM-DD", "cagr": float, "elapsed_days": int}
    """
    if len(dates) != len(values):
        raise ValueError("dates and values must have identical lengths.")
    if len(values) < 2:
        return []
    if window_years < 1:
        raise ValueError("window_years must be at least 1.")

    parsed_dates: list[datetime.date] = []
    for d in dates:
        if isinstance(d, datetime.date):
            parsed_dates.append(d)
        else:
            parsed_dates.append(datetime.date.fromisoformat(str(d)))

    # Map dates to values and sort chronologically
    date_val_map: dict[datetime.date, float] = {}
    for d, v in zip(parsed_dates, values):
        if not math.isfinite(v) or v <= 0:
            raise ValueError("All values must be finite and positive.")
        date_val_map[d] = float(v)

    sorted_dates = sorted(date_val_map.keys())
    results: list[dict[str, Any]] = []

    for end_d in sorted_dates:
        # Determine calendar target start date
        try:
            target_start = end_d.replace(year=end_d.year - window_years)
        except ValueError:
            # Handle leap year edge case (Feb 29 -> Feb 28)
            target_start = end_d.replace(year=end_d.year - window_years, day=28)

        # Look back up to max_lookback_days for weekend / market holidays
        found_start: datetime.date | None = None
        for offset in range(max_lookback_days + 1):
            cand = target_start - datetime.timedelta(days=offset)
            if cand in date_val_map:
                found_start = cand
                break

        if found_start is not None:
            v_start = date_val_map[found_start]
            v_end = date_val_map[end_d]
            elapsed_days = (end_d - found_start).days
            if elapsed_days > 0 and v_start > 0 and v_end > 0:
                cagr_val = math.pow(v_end / v_start, 365.25 / float(elapsed_days)) - 1.0
                results.append({
                    "start_date": found_start.isoformat(),
                    "end_date": end_d.isoformat(),
                    "cagr": cagr_val,
                    "elapsed_days": elapsed_days,
                })

    return results


def rolling_return_distribution(cagrs: Sequence[float]) -> dict[str, Any]:
    """
    Computes statistical distribution across rolling CAGR observations (§RET-05).
    Includes: count, mean, median, min, max, 25th percentile, 75th percentile, and sample std dev.
    """
    if len(cagrs) == 0:
        return {
            "count": 0,
            "mean": None,
            "median": None,
            "min": None,
            "max": None,
            "p25": None,
            "p75": None,
            "std_dev": None,
        }

    valid_cagrs = [float(x) for x in cagrs if math.isfinite(x)]
    n = len(valid_cagrs)
    if n == 0:
        return {
            "count": 0,
            "mean": None,
            "median": None,
            "min": None,
            "max": None,
            "p25": None,
            "p75": None,
            "std_dev": None,
        }

    mean_val = sum(valid_cagrs) / n
    sorted_cagrs = sorted(valid_cagrs)

    # Median
    if n % 2 == 1:
        median_val = sorted_cagrs[n // 2]
    else:
        median_val = (sorted_cagrs[(n // 2) - 1] + sorted_cagrs[n // 2]) / 2.0

    min_val = sorted_cagrs[0]
    max_val = sorted_cagrs[-1]

    # Percentiles using nearest rank / linear position
    idx_p25 = max(0, min(int(n * 0.25), n - 1))
    idx_p75 = max(0, min(int(n * 0.75), n - 1))
    p25_val = sorted_cagrs[idx_p25]
    p75_val = sorted_cagrs[idx_p75]

    # Sample standard deviation (N - 1 denominator)
    if n >= 2:
        variance = sum((x - mean_val) ** 2 for x in valid_cagrs) / (n - 1)
        std_dev = math.sqrt(variance)
    else:
        std_dev = 0.0

    return {
        "count": n,
        "mean": mean_val,
        "median": median_val,
        "min": min_val,
        "max": max_val,
        "p25": p25_val,
        "p75": p75_val,
        "std_dev": std_dev,
    }


def rolling_outperformance(
    fund_windows: Sequence[dict[str, Any]],
    bench_windows: Sequence[dict[str, Any]],
) -> dict[str, Any]:
    """
    Evaluates rolling active outperformance against benchmark (§RET-06).
    Pairs windows synchronously on ending calendar date:
        fund_cagr > bench_cagr (Strict inequality: no ties count as outperformance).
    """
    fund_map = {w["end_date"]: w["cagr"] for w in fund_windows}
    bench_map = {w["end_date"]: w["cagr"] for w in bench_windows}

    paired_dates = sorted(set(fund_map.keys()) & set(bench_map.keys()))
    paired_count = len(paired_dates)

    if paired_count == 0:
        return {
            "paired_windows": 0,
            "outperforming_windows": 0,
            "underperforming_windows": 0,
            "outperformance_percentage": None,
            "mean_excess_return": None,
        }

    outperform_count = 0
    excess_returns: list[float] = []

    for d in paired_dates:
        f_ret = fund_map[d]
        b_ret = bench_map[d]
        excess = f_ret - b_ret
        excess_returns.append(excess)
        # Strict inequality per §RET-06: no ties count as outperformance
        if f_ret > b_ret:
            outperform_count += 1

    underperform_count = paired_count - outperform_count
    outperform_pct = (outperform_count / float(paired_count)) * 100.0
    mean_excess = sum(excess_returns) / float(paired_count) if paired_count > 0 else 0.0

    return {
        "paired_windows": paired_count,
        "outperforming_windows": outperform_count,
        "underperforming_windows": underperform_count,
        "outperformance_percentage": outperform_pct,
        "mean_excess_return": mean_excess,
    }


def compute_rolling_windows(
    dates: Sequence[str | datetime.date],
    values: Sequence[float],
    period_type: str = "3Y",
) -> dict[str, Any]:
    """
    Wrapper for computing rolling return distribution for a given period type.
    """
    allowed_periods = {"1Y": 1, "3Y": 3, "5Y": 5}
    if period_type not in allowed_periods:
        raise ValueError(f"Unsupported period: {period_type}")
    window_years = allowed_periods[period_type]
    windows = compute_rolling_cagrs(dates, values, window_years=window_years)
    dist = rolling_return_distribution([w["cagr"] for w in windows])
    status = "CALCULATED" if dist["count"] >= 450 else "INSUFFICIENT_DATA"
    return {
        "metric_code": "RET-05",
        "period_type": period_type,
        "status": status,
        "windows_calculated": dist["count"],
        "distribution": dist,
    }


def compute_rolling_excess_returns(
    fund_dates: Sequence[str | datetime.date],
    fund_navs: Sequence[float],
    bench_dates: Sequence[str | datetime.date],
    bench_navs: Sequence[float],
    period_type: str = "3Y",
) -> dict[str, Any]:
    """
    Wrapper for computing rolling excess return comparison vs benchmark.
    """
    allowed_periods = {"1Y": 1, "3Y": 3, "5Y": 5}
    if period_type not in allowed_periods:
        raise ValueError(f"Unsupported period: {period_type}")
    window_years = allowed_periods[period_type]
    f_wins = compute_rolling_cagrs(fund_dates, fund_navs, window_years=window_years)
    b_wins = compute_rolling_cagrs(bench_dates, bench_navs, window_years=window_years)
    outperf = rolling_outperformance(f_wins, b_wins)
    status = "CALCULATED" if outperf["paired_windows"] >= 450 else "INSUFFICIENT_DATA"
    return {
        "metric_code": "RET-06",
        "period_type": period_type,
        "status": status,
        **outperf,
    }


def compute_rolling_cagr(
    dates: Sequence[str | datetime.date],
    values: Sequence[float],
    window_years: int = 3,
) -> dict[str, Any]:
    """
    Wrapper for computing rolling CAGR series and status.
    """
    if len(values) < 450:
        return {
            "status": "INSUFFICIENT_DATA",
            "total_windows": 0,
            "windows": [],
        }
    windows = compute_rolling_cagrs(dates, values, window_years=window_years)
    status = "CALCULATED" if len(windows) >= 450 else "INSUFFICIENT_DATA"
    return {
        "status": status,
        "total_windows": len(windows),
        "windows": windows,
    }