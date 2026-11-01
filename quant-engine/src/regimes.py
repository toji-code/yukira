"""
Deterministic Market Condition Classification & Historical Stress Regime Analytics.

Implements Workstream 2 of YUKIRA V1:
- Deterministic classification based purely on historical primary benchmark returns.
- Zero Large Language Model classification or predictive regime forecasting.
- Point-in-time boundary isolation.
- Disclosures distinguishing benchmark return direction from economic causality.
"""

from __future__ import annotations

import datetime
import math
from typing import Any, Sequence


def classify_daily_conditions(
    benchmark_returns: Sequence[float],
) -> dict[str, Any]:
    """
    Classify daily trading observations into discrete market conditions.
    - Up days: R_benchmark > 0.0
    - Down days: R_benchmark < 0.0
    - Flat days: R_benchmark == 0.0
    """
    total = len(benchmark_returns)
    if total == 0:
        return {
            "total_days": 0,
            "up_days": 0,
            "down_days": 0,
            "flat_days": 0,
            "up_percentage": 0.0,
            "down_percentage": 0.0,
            "flat_percentage": 0.0,
        }

    up_count = sum(1 for r in benchmark_returns if r > 0.0)
    down_count = sum(1 for r in benchmark_returns if r < 0.0)
    flat_count = total - (up_count + down_count)

    return {
        "total_days": total,
        "up_days": up_count,
        "down_days": down_count,
        "flat_days": flat_count,
        "up_percentage": (up_count / float(total)) * 100.0,
        "down_percentage": (down_count / float(total)) * 100.0,
        "flat_percentage": (flat_count / float(total)) * 100.0,
    }


def classify_rolling_market_regimes(
    benchmark_dates: Sequence[str | datetime.date],
    benchmark_values: Sequence[float],
    window_days: int = 21,
    advance_threshold: float = 0.05,
    decline_threshold: float = -0.05,
) -> dict[str, Any]:
    """
    Classifies trailing rolling phases of benchmark performance over window_days (default 21 trading days ~ 1 month).
    - ADVANCE: Trailing window return >= +5.0%
    - DECLINE: Trailing window return <= -5.0%
    - SIDEWAYS: Trailing window return between -5.0% and +5.0%
    """
    n = len(benchmark_values)
    if n < window_days or window_days < 2:
        return {
            "total_windows": 0,
            "advance_windows": 0,
            "decline_windows": 0,
            "sideways_windows": 0,
            "advance_percentage": 0.0,
            "decline_percentage": 0.0,
            "sideways_percentage": 0.0,
            "status": "INSUFFICIENT_DATA",
            "methodology_disclaimer": "Deterministic benchmark phase classification based on 21-day trailing returns. Does not establish macroeconomic causality.",
        }

    advance_count = 0
    decline_count = 0
    sideways_count = 0
    total_windows = n - window_days + 1

    for i in range(total_windows):
        v_start = benchmark_values[i]
        v_end = benchmark_values[i + window_days - 1]
        if v_start > 0:
            ret = (v_end / v_start) - 1.0
            if ret >= advance_threshold:
                advance_count += 1
            elif ret <= decline_threshold:
                decline_count += 1
            else:
                sideways_count += 1

    return {
        "total_windows": total_windows,
        "window_days": window_days,
        "advance_windows": advance_count,
        "decline_windows": decline_count,
        "sideways_windows": sideways_count,
        "advance_percentage": (advance_count / float(total_windows)) * 100.0 if total_windows > 0 else 0.0,
        "decline_percentage": (decline_count / float(total_windows)) * 100.0 if total_windows > 0 else 0.0,
        "sideways_percentage": (sideways_count / float(total_windows)) * 100.0 if total_windows > 0 else 0.0,
        "status": "CALCULATED",
        "methodology_disclaimer": "Deterministic benchmark phase classification based on 21-day trailing returns. Does not establish macroeconomic causality.",
    }


# Authoritative pre-specified retrospective Indian market regimes
CANONICAL_MARKET_REGIMES = [
    {
        "regime_id": "REG-01",
        "name": "Liquidity Shock & Rapid Rebound",
        "start_date": "2020-02-01",
        "end_date": "2020-11-30",
        "benchmark_basis": "Nifty benchmark peak-to-trough collapse exceeding 20% and subsequent swift liquidity-driven recovery.",
        "inclusion_rule": "Continuous trading days >= 126; requires valid daily observations across episode.",
    },
    {
        "regime_id": "REG-02",
        "name": "Sustained Secular Bull Market",
        "start_date": "2020-12-01",
        "end_date": "2021-12-31",
        "benchmark_basis": "Benchmark annualized return exceeding +20% with maximum drawdown contained under 10%.",
        "inclusion_rule": "Continuous trading days >= 126; verifies upside compounding and low volatility regime.",
    },
    {
        "regime_id": "REG-03",
        "name": "Monetary Tightening & Sideways Consolidation",
        "start_date": "2022-01-01",
        "end_date": "2023-03-31",
        "benchmark_basis": "Global and RBI repo rate hike cycle (+250 bps) accompanied by range-bound index trading.",
        "inclusion_rule": "Continuous trading days >= 126; evaluates capital preservation during non-trending markets.",
    },
    {
        "regime_id": "REG-04",
        "name": "Broad-Based Equity Expansion",
        "start_date": "2023-04-01",
        "end_date": "2024-01-15",
        "benchmark_basis": "Broad market advance with expanding market breadth and distinct category dispersion.",
        "inclusion_rule": "Continuous trading days >= 126; evaluates relative alpha capture during broad rally.",
    },
]


def evaluate_historical_regimes(
    fund_dates: Sequence[str | datetime.date],
    fund_values: Sequence[float],
    benchmark_dates: Sequence[str | datetime.date],
    benchmark_values: Sequence[float],
    as_of_date: str | datetime.date | None = None,
) -> list[dict[str, Any]]:
    """
    Evaluates fund and benchmark performance across pre-specified retrospective market regimes.
    All calculations are deterministic and point-in-time isolated.
    """
    if len(fund_dates) != len(fund_values) or len(benchmark_dates) != len(benchmark_values):
        raise ValueError("Dates and values sequences must have matching lengths.")

    fund_map: dict[datetime.date, float] = {}
    for d, v in zip(fund_dates, fund_values):
        if math.isfinite(v) and v > 0:
            parsed_d = d if isinstance(d, datetime.date) else datetime.date.fromisoformat(str(d))
            fund_map[parsed_d] = float(v)

    bench_map: dict[datetime.date, float] = {}
    for d, v in zip(benchmark_dates, benchmark_values):
        if math.isfinite(v) and v > 0:
            parsed_d = d if isinstance(d, datetime.date) else datetime.date.fromisoformat(str(d))
            bench_map[parsed_d] = float(v)

    parsed_as_of = None
    if as_of_date:
        parsed_as_of = as_of_date if isinstance(as_of_date, datetime.date) else datetime.date.fromisoformat(str(as_of_date))

    results: list[dict[str, Any]] = []

    for reg in CANONICAL_MARKET_REGIMES:
        reg_start = datetime.date.fromisoformat(reg["start_date"])
        reg_end = datetime.date.fromisoformat(reg["end_date"])
        if parsed_as_of and reg_end > parsed_as_of:
            reg_end = parsed_as_of

        f_subset_dates = sorted([d for d in fund_map.keys() if reg_start <= d <= reg_end])
        b_subset_dates = sorted([d for d in bench_map.keys() if reg_start <= d <= reg_end])

        trading_days = len(f_subset_dates)
        fund_cagr = None
        bench_cagr = None
        excess_cagr = None
        fund_mdd = None
        bench_mdd = None

        if len(f_subset_dates) >= 2:
            d_start = f_subset_dates[0]
            d_end = f_subset_dates[-1]
            elapsed_days = (d_end - d_start).days
            if elapsed_days > 0:
                v_start = fund_map[d_start]
                v_end = fund_map[d_end]
                fund_cagr = math.pow(v_end / v_start, 365.25 / float(elapsed_days)) - 1.0

            # Calculate Max Drawdown for fund during regime
            f_vals = [fund_map[d] for d in f_subset_dates]
            peak = f_vals[0]
            max_drop = 0.0
            for val in f_vals:
                if val > peak:
                    peak = val
                drop = (val - peak) / peak if peak > 0 else 0.0
                if drop < max_drop:
                    max_drop = drop
            fund_mdd = max_drop

        if len(b_subset_dates) >= 2:
            d_start = b_subset_dates[0]
            d_end = b_subset_dates[-1]
            elapsed_days = (d_end - d_start).days
            if elapsed_days > 0:
                v_start = bench_map[d_start]
                v_end = bench_map[d_end]
                bench_cagr = math.pow(v_end / v_start, 365.25 / float(elapsed_days)) - 1.0

            b_vals = [bench_map[d] for d in b_subset_dates]
            peak = b_vals[0]
            max_drop = 0.0
            for val in b_vals:
                if val > peak:
                    peak = val
                drop = (val - peak) / peak if peak > 0 else 0.0
                if drop < max_drop:
                    max_drop = drop
            bench_mdd = max_drop

        coverage_status = "INSUFFICIENT_DATA"
        if fund_cagr is not None and bench_cagr is not None:
            excess_cagr = fund_cagr - bench_cagr
            coverage_status = "VERIFIED_REGIME_COVERAGE" if trading_days >= 100 else "PARTIAL_DATA"
        elif fund_cagr is not None:
            coverage_status = "BENCHMARK_UNAVAILABLE"

        results.append({
            "regime_id": reg["regime_id"],
            "name": reg["name"],
            "period": f"{reg['start_date']} to {reg['end_date']}",
            "benchmark_basis": reg["benchmark_basis"],
            "inclusion_rule": reg["inclusion_rule"],
            "fund_cagr": fund_cagr,
            "benchmark_cagr": bench_cagr,
            "excess_return": excess_cagr,
            "fund_max_drawdown": fund_mdd,
            "benchmark_max_drawdown": bench_mdd,
            "trading_days": trading_days,
            "coverage_status": coverage_status,
            "retrospective_disclaimer": "Pre-specified retrospective regime classification based on historical benchmark return boundaries. Past regime behavior does not guarantee future persistence and does not constitute economic regime prediction.",
        })

    return results

