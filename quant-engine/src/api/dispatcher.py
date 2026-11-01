from __future__ import annotations

import datetime
import math
from typing import Any, Dict, List, Optional, Tuple

from src.api.models import CalculationRequest, CalculationStatus, MetricOutputItem
from src import (
    active_return as active_return_mod,
    alpha,
    beta,
    capture,
    downside_beta,
    expected_shortfall,
    information_ratio,
    ratios,
    regimes,
    returns as ret_mod,
    risk,
    risk_free,
    rolling,
    semideviation,
    statistics,
    tracking_error,
    treynor,
    ulcer_index,
    var,
)


def dispatch_calculation(request: CalculationRequest) -> List[MetricOutputItem]:
    """
    Executes requested quantitative metrics deterministically using existing pure-Python modules.
    Adheres strictly to the YUKIRA governance mandate:
      - Deterministic execution only
      - Zero AI calculation
      - Explicit diagnostic convention reporting
    """
    results: List[MetricOutputItem] = []

    if not request.nav_series or len(request.nav_series) < 2:
        for code in request.metric_codes:
            results.append(
                MetricOutputItem(
                    metric_code=code,
                    period_type="1Y",
                    numeric_value=None,
                    units="UNDEFINED",
                    status=CalculationStatus.INSUFFICIENT_DATA,
                    error_message="At least two NAV observations are required.",
                )
            )
        return results

    # Sort observations chronologically by effective_date
    sorted_nav = sorted(request.nav_series, key=lambda x: x.effective_date)
    nav_values = [obs.value for obs in sorted_nav]
    dates = [obs.effective_date for obs in sorted_nav]

    # Compute periodic simple returns
    try:
        fund_returns = statistics.periodic_returns(nav_values)
    except Exception as e:
        for code in request.metric_codes:
            results.append(
                MetricOutputItem(
                    metric_code=code,
                    units="UNDEFINED",
                    status=CalculationStatus.ERROR,
                    error_message=f"Failed to compute periodic returns: {str(e)}",
                )
            )
        return results

    # Handle optional benchmark series
    bench_returns: Optional[List[float]] = None
    bench_values: Optional[List[float]] = None
    aligned_dates: Optional[List[str]] = None
    if request.benchmark_series and len(request.benchmark_series) >= 2:
        sorted_bench = sorted(request.benchmark_series, key=lambda x: x.effective_date)
        bench_map = {obs.effective_date: obs.value for obs in sorted_bench}
        aligned_nav = []
        aligned_bench = []
        aligned_dates = []
        for d, v in zip(dates, nav_values):
            if d in bench_map:
                aligned_nav.append(v)
                aligned_bench.append(bench_map[d])
                aligned_dates.append(d)
        if len(aligned_nav) >= 2:
            bench_values = aligned_bench
            bench_returns = statistics.periodic_returns(bench_values)
            # Re-align fund returns to benchmark dates for relative metrics
            aligned_fund_returns = statistics.periodic_returns(aligned_nav)
        else:
            aligned_fund_returns = fund_returns
    else:
        aligned_fund_returns = fund_returns

    # Parameters & conventions
    params = request.parameters or {}
    horizon = str(params.get("horizon", "3Y")).upper()
    if horizon not in ("3Y", "5Y", "10Y"):
        horizon = "3Y"
    default_min_obs = 700 if horizon == "3Y" else (1200 if horizon == "5Y" else 2400)
    rf_annual = float(params.get("risk_free_rate", 0.065))  # Candidate 6.5% default
    periods_per_year = float(params.get("periods_per_year", 252.0))
    rf_periodic = rf_annual / periods_per_year

    # Handle optional risk-free series
    aligned_rf_returns: Optional[List[float]] = None
    aligned_rf_returns_relative: Optional[List[float]] = None
    rf_alignment_error: Optional[str] = None
    rf_obs_list: Optional[List[risk_free.RiskFreeObservation]] = None

    if request.risk_free_series and len(request.risk_free_series) >= 1:
        try:
            rf_obs_list = [
                risk_free.RiskFreeObservation(
                    effective_date=datetime.date.fromisoformat(obs.effective_date),
                    quoted_yield=obs.value,
                    availability_time=datetime.datetime.fromisoformat(obs.availability_time),
                    revision_seq=obs.revision_seq,
                )
                for obs in request.risk_free_series
            ]
            aligned_rf_returns = risk_free.align_risk_free_series(
                observation_dates=dates,
                risk_free_observations=rf_obs_list,
                analysis_cutoff=request.as_of_date,
                knowledge_cutoff=request.knowledge_cutoff_time,
                max_lookback_days=4,
            )
            if aligned_dates and aligned_dates != dates:
                aligned_rf_returns_relative = risk_free.align_risk_free_series(
                    observation_dates=aligned_dates,
                    risk_free_observations=rf_obs_list,
                    analysis_cutoff=request.as_of_date,
                    knowledge_cutoff=request.knowledge_cutoff_time,
                    max_lookback_days=4,
                )
            else:
                aligned_rf_returns_relative = aligned_rf_returns
        except Exception as e:
            rf_alignment_error = str(e)
            aligned_rf_returns = None
            aligned_rf_returns_relative = None

    # Elapsed years for CAGR
    try:
        d_start = datetime.date.fromisoformat(dates[0])
        d_end = datetime.date.fromisoformat(dates[-1])
        elapsed_days = (d_end - d_start).days
        elapsed_years = max(elapsed_days / 365.25, 1.0 / periods_per_year)
    except Exception:
        elapsed_years = len(fund_returns) / periods_per_year

    def _cagr_window(metric_code: str, lookback_calendar_days: int, min_observations: int):
        """
        Resolve the trailing calendar window for a CAGR metric and compute it deterministically.

        The window is anchored at the LAST available PIT-resolved observation (the analysis
        cutoff) and walks BACKWARD by `lookback_calendar_days`. The first observation at or
        after that boundary is the window start. No observation beyond the cutoff is ever
        consulted, so no look-ahead bias can be introduced.

        Returns a MetricOutputItem either way, so a short window surfaces as an explicit
        INSUFFICIENT_DATA state rather than as a silently mislabelled longer-window value.
        """
        end_idx = len(dates) - 1
        try:
            anchor = datetime.date.fromisoformat(dates[end_idx])
            boundary = anchor - datetime.timedelta(days=lookback_calendar_days)
            start_idx = 0
            for i in range(end_idx, -1, -1):
                if datetime.date.fromisoformat(dates[i]) <= boundary:
                    start_idx = i
                    break
            else:
                start_idx = 0
        except Exception:
            start_idx = 0

        w_dates = dates[start_idx:]
        w_values = nav_values[start_idx:]
        obs_count = len(w_values)

        declared_years = lookback_calendar_days / 365.25
        common = {
            "methodology_status": "CANDIDATE",
            "lookback_calendar_days": lookback_calendar_days,
            "declared_lookback_years": declared_years,
            "window_start_date": w_dates[0],
            "window_end_date": w_dates[-1],
            "window_observation_count": obs_count,
            "min_observations_required": min_observations,
            "window_anchor_convention": "LAST_PIT_RESOLVED_OBSERVATION",
            "annualization_convention": "JULIAN_365.25_CANDIDATE",
            "lookahead_bias_control": "ANCHORED_AT_CUTOFF_BACKWARD_WALK_ONLY",
        }

        if obs_count < 2 or obs_count < min_observations:
            return MetricOutputItem(
                metric_code=metric_code,
                period_type=f"{int(round(declared_years))}Y",
                numeric_value=None,
                units="PERCENTAGE",
                status=CalculationStatus.INSUFFICIENT_DATA,
                error_message=(
                    f"Insufficient observations for {metric_code}: {obs_count} provided in the "
                    f"{lookback_calendar_days}-calendar-day trailing window, "
                    f"minimum {min_observations} required."
                ),
                diagnostics=common,
            )

        try:
            s_date = datetime.date.fromisoformat(w_dates[0])
            e_date = datetime.date.fromisoformat(w_dates[-1])
            win_days = (e_date - s_date).days
            win_years = max(win_days / 365.25, 1.0 / periods_per_year)
        except Exception:
            win_days = int(round((obs_count - 1) * 365.25 / periods_per_year))
            win_years = max((obs_count - 1) / periods_per_year, 1.0 / periods_per_year)

        val = ret_mod.cagr(w_values[0], w_values[-1], win_years)
        diag = dict(common)
        diag.update({"elapsed_years": win_years, "elapsed_calendar_days": win_days})
        return MetricOutputItem(
            metric_code=metric_code,
            period_type=f"{int(round(declared_years))}Y",
            numeric_value=val,
            units="PERCENTAGE",
            status=CalculationStatus.CALCULATED,
            diagnostics=diag,
        )

    for code in request.metric_codes:
        try:
            # RET-03 window ownership: PeriodReturnCalculationService selects the boundary
            # observations under PIT rules and supplies exactly that window to the engine
            # (see Ret03EndToEndIntegrationTest). Whole-series semantics are therefore correct
            # and authoritative for RET-03, and are preserved unchanged.
            #
            # RET-01 and RET-04 previously received the SAME whole-series CAGR as RET-03 while
            # being labelled "1Y" and "5Y" respectively. That is a material misrepresentation of
            # the observation window, so each now derives its own trailing calendar window.
            if code == "RET-01":  # 1Y CAGR - trailing 12 calendar months
                results.append(_cagr_window(code, lookback_calendar_days=365, min_observations=240))

            elif code == "RET-04":  # 5Y CAGR - trailing 60 calendar months
                results.append(_cagr_window(code, lookback_calendar_days=1826, min_observations=1200))

            elif code == "RET-03":  # CAGR - caller-supplied window
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=(
                                f"Insufficient observations for RET-03 ({horizon}): {len(nav_values)} provided, "
                                f"minimum {min_obs} required."
                            ),
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "window_start_date": dates[0],
                                "window_end_date": dates[-1],
                            },
                        )
                    )
                else:
                    val = ret_mod.cagr(nav_values[0], nav_values[-1], elapsed_years)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "elapsed_years": elapsed_years,
                                "lookback_calendar_days": 1095 if horizon == "3Y" else (1826 if horizon == "5Y" else 3652),
                                "window_start_date": dates[0],
                                "window_end_date": dates[-1],
                                "window_observation_count": len(nav_values),
                                "window_anchor_convention": "CALLER_SUPPLIED_PIT_BOUNDARY_PAIR",
                                "annualization_convention": "JULIAN_365.25_CANDIDATE",
                                "lookahead_bias_control": "ANCHORED_AT_CUTOFF_BACKWARD_WALK_ONLY",
                            },
                        )
                    )

            elif code == "RET-02":  # Total Return (Operational Baseline)
                val = ret_mod.period_return(nav_values[0], nav_values[-1])
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        period_type=horizon,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={
                            "methodology_status": "OPERATIONAL_BASELINE",
                            "requested_horizon": horizon,
                            "beginning_observation_date": dates[0] if dates else None,
                            "ending_observation_date": dates[-1] if dates else None,
                            "beginning_nav": float(nav_values[0]),
                            "ending_nav": float(nav_values[-1]),
                            "observation_count": len(nav_values),
                            "synthetic_data_filling": False,
                            "convention": "DISCRETE_PERIOD_ARITHMETIC",
                        },
                    )
                )

            elif code == "RET-07":  # Annualized Active Return
                if not bench_values or len(bench_values) < 2 or len(nav_values) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark values required for Active Return.",
                        )
                    )
                else:
                    fund_cagr = ret_mod.cagr(nav_values[0], nav_values[-1], elapsed_years)
                    bench_cagr = ret_mod.cagr(bench_values[0], bench_values[-1], elapsed_years)
                    val = fund_cagr - bench_cagr
                    fund_simple = ret_mod.period_return(nav_values[0], nav_values[-1])
                    bench_simple = ret_mod.period_return(bench_values[0], bench_values[-1])
                    tracking_diff = fund_simple - bench_simple
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "fund_return": fund_cagr,
                                "benchmark_return": bench_cagr,
                                "excess_return": val,
                                "fund_cagr": fund_cagr,
                                "benchmark_cagr": bench_cagr,
                                "fund_simple_period_return": fund_simple,
                                "benchmark_simple_period_return": bench_simple,
                                "tracking_difference": tracking_diff,
                                "elapsed_years": elapsed_years,
                                "requested_horizon": horizon,
                                "methodology_status": "CANDIDATE",
                                "paired_count": len(bench_values) - 1 if len(bench_values) > 1 else len(bench_values),
                                "observation_count": len(nav_values),
                                "attribution_disclaimer": "Active return is an empirical excess growth rate over the primary benchmark. It does not isolate skill from factor tilt or beta and has no predictive power.",
                            },
                        )
                    )

            elif code == "RET-05":  # 3Y Rolling Return Mean & Distribution
                window_years = int(params.get("window_years", 3))
                min_windows = int(params.get("min_windows", 450))
                max_lookback_days = int(params.get("max_lookback_days", 4))
                fund_windows = rolling.compute_rolling_cagrs(dates, nav_values, window_years, max_lookback_days)
                cagrs = [w["cagr"] for w in fund_windows]
                dist = rolling.rolling_return_distribution(cagrs)

                if dist["count"] < min_windows:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=f"{window_years}Y",
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient rolling windows for RET-05: {dist['count']} provided, minimum {min_windows} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "window_years": window_years,
                                "valid_window_count": dist["count"],
                                "min_windows_required": min_windows,
                                "mean": dist["mean"],
                                "median": dist["median"],
                                "min": dist["min"],
                                "max": dist["max"],
                                "p25": dist["p25"],
                                "p75": dist["p75"],
                                "std_dev": dist["std_dev"],
                            },
                        )
                    )
                else:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=f"{window_years}Y",
                            numeric_value=dist["mean"],
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "window_years": window_years,
                                "valid_window_count": dist["count"],
                                "min_windows_required": min_windows,
                                "mean": dist["mean"],
                                "median": dist["median"],
                                "min": dist["min"],
                                "max": dist["max"],
                                "p10": dist.get("p10"),
                                "p25": dist["p25"],
                                "p75": dist["p75"],
                                "p90": dist.get("p90"),
                                "std_dev": dist["std_dev"],
                                "positive_count": dist.get("positive_count"),
                                "negative_count": dist.get("negative_count"),
                                "positive_percentage": dist.get("positive_percentage"),
                                "negative_percentage": dist.get("negative_percentage"),
                                "annualization_convention": "365.25_JULIAN_CANDIDATE",
                                "lookback_convention": "4_DAY_CALENDAR_WINDOW_CANDIDATE",
                                "serial_autocorrelation_disclosure": "Adjacent daily rolling returns share ~99.8% identical data; statistical significance tests require Newey-West adjustment.",
                            },
                        )
                    )

            elif code == "RET-06":  # Rolling Outperformance % vs Benchmark
                window_years = int(params.get("window_years", 3))
                min_windows = int(params.get("min_windows", 450))
                max_lookback_days = int(params.get("max_lookback_days", 4))

                if not request.benchmark_series or len(request.benchmark_series) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=f"{window_years}Y",
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Synchronous benchmark observations required for RET-06.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "window_years": window_years,
                                "benchmark_present": False,
                                "min_windows_required": min_windows,
                            },
                        )
                    )
                else:
                    fund_windows = rolling.compute_rolling_cagrs(dates, nav_values, window_years, max_lookback_days)
                    sorted_bench_obs = sorted(request.benchmark_series, key=lambda x: x.effective_date)
                    b_dates = [b.effective_date for b in sorted_bench_obs]
                    b_vals = [b.value for b in sorted_bench_obs]
                    bench_windows = rolling.compute_rolling_cagrs(b_dates, b_vals, window_years, max_lookback_days)
                    outperf = rolling.rolling_outperformance(fund_windows, bench_windows)

                    if outperf["paired_windows"] < min_windows:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=f"{window_years}Y",
                                numeric_value=None,
                                units="PERCENTAGE",
                                status=CalculationStatus.INSUFFICIENT_DATA,
                                error_message=f"Insufficient paired rolling windows for RET-06: {outperf['paired_windows']} paired, minimum {min_windows} required.",
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "window_years": window_years,
                                    "paired_windows": outperf["paired_windows"],
                                    "min_windows_required": min_windows,
                                    "outperforming_windows": outperf["outperforming_windows"],
                                    "underperforming_windows": outperf["underperforming_windows"],
                                    "outperformance_percentage": outperf["outperformance_percentage"],
                                    "mean_excess_return": outperf["mean_excess_return"],
                                    "median_excess_return": outperf.get("median_excess_return"),
                                    "min_excess_return": outperf.get("min_excess_return"),
                                    "max_excess_return": outperf.get("max_excess_return"),
                                    "equality_rule": "STRICT_INEQUALITY_NO_TIES_COUNT_AS_OUTPERFORMANCE",
                                    "benchmark_integrity_disclosure": "Synchronous calendar alignment applied without date fabrication.",
                                },
                            )
                        )
                    else:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=f"{window_years}Y",
                                numeric_value=outperf["outperformance_percentage"],
                                units="PERCENTAGE",
                                status=CalculationStatus.CALCULATED,
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "window_years": window_years,
                                    "paired_windows": outperf["paired_windows"],
                                    "min_windows_required": min_windows,
                                    "outperforming_windows": outperf["outperforming_windows"],
                                    "underperforming_windows": outperf["underperforming_windows"],
                                    "outperformance_percentage": outperf["outperformance_percentage"],
                                    "mean_excess_return": outperf["mean_excess_return"],
                                    "median_excess_return": outperf.get("median_excess_return"),
                                    "min_excess_return": outperf.get("min_excess_return"),
                                    "max_excess_return": outperf.get("max_excess_return"),
                                    "equality_rule": "STRICT_INEQUALITY_NO_TIES_COUNT_AS_OUTPERFORMANCE",
                                    "benchmark_integrity_disclosure": "Synchronous calendar alignment applied without date fabrication.",
                                },
                            )
                        )

            elif code == "RSK-01":  # Annualized Volatility
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-01 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "periods_per_year": periods_per_year,
                            },
                        )
                    )
                else:
                    val = statistics.volatility(fund_returns, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "periods_per_year": periods_per_year,
                                "annualization_convention": "SQRT_252_CANDIDATE",
                                "denominator_convention": "N_MINUS_ONE_CANDIDATE",
                                "observation_count": len(nav_values),
                                "return_count": len(fund_returns),
                                "sample_std_daily": statistics.stdev(fund_returns) if len(fund_returns) >= 2 else 0.0,
                            },
                        )
                    )

            elif code == "RSK-02":  # Downside Semideviation
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                target_return = float(params.get("target_return", 0.0))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-02 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "target_return_mar": target_return,
                                "periods_per_year": periods_per_year,
                            },
                        )
                    )
                else:
                    val = semideviation.downside_semideviation(
                        fund_returns, target_return=target_return, periods_per_year=periods_per_year
                    )
                    downside_count = sum(1 for r in fund_returns if r < target_return)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "target_return_mar": target_return,
                                "periods_per_year": periods_per_year,
                                "annualization_convention": "SQRT_252_CANDIDATE",
                                "denominator_convention": "N_MINUS_ONE_CANDIDATE",
                                "observation_count": len(nav_values),
                                "return_count": len(fund_returns),
                                "downside_count": downside_count,
                            },
                        )
                    )

            elif code == "RSK-03":  # Maximum Drawdown
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-03 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                            },
                        )
                    )
                else:
                    details = risk.drawdown_details(nav_values, dates)
                    bm_for_episodes = aligned_bench if (aligned_dates == dates) else None
                    episodes = risk.extract_drawdown_episodes(
                        nav_values,
                        dates,
                        benchmark_values=bm_for_episodes,
                        top_n=5,
                        min_drawdown=0.02
                    )
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=details["max_drawdown"],
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "peak_nav": details["peak_nav"],
                                "trough_nav": details["trough_nav"],
                                "peak_date": details["peak_date"],
                                "trough_date": details["trough_date"],
                                "is_recovered": details.get("is_recovered", False),
                                "recovery_date": details.get("recovery_date"),
                                "recovery_trading_days": details.get("recovery_trading_days"),
                                "recovery_status": details.get("recovery_status", "UNKNOWN"),
                                "running_peak_denominator_convention": "RUNNING_PEAK_NAV_CANDIDATE",
                                "drawdown_episodes": episodes,
                                "drawdown_episodes_count": len(episodes),
                            },
                        )
                    )

            elif code == "RSK-04":  # Maximum Drawdown Duration
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="DAYS",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-04 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                            },
                        )
                    )
                else:
                    duration_info = risk.maximum_drawdown_duration(nav_values, dates)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=float(duration_info["max_duration_calendar_days"]),
                            units="DAYS",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "max_duration_calendar_days": duration_info["max_duration_calendar_days"],
                                "max_duration_trading_days": duration_info["max_duration_trading_days"],
                                "is_ongoing_at_cutoff": duration_info["is_ongoing_at_cutoff"],
                                "worst_episode_peak_date": duration_info["worst_episode_peak_date"],
                                "worst_episode_recovery_date": duration_info["worst_episode_recovery_date"],
                                "worst_episode_trough_date": duration_info["worst_episode_trough_date"],
                                "worst_episode_trough_nav": duration_info["worst_episode_trough_nav"],
                            },
                        )
                    )

            elif code == "RSK-05":  # Ulcer Index
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="POINTS",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-05 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                            },
                        )
                    )
                else:
                    val = ulcer_index.ulcer_index(nav_values)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="POINTS",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "denominator_convention": "N_OBSERVATIONS_CANDIDATE",
                            },
                        )
                    )

            elif code == "RSK-06":  # Historical VaR 95%
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                confidence_level = float(params.get("confidence_level", 0.95))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-06 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "confidence_level": confidence_level,
                            },
                        )
                    )
                else:
                    val = var.historical_var(fund_returns, confidence_level)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "confidence_level": confidence_level,
                                "observation_count": len(nav_values),
                                "return_count": len(fund_returns),
                                "min_observations_required": min_obs,
                                "annualization_convention": "NONE_1DAY_HORIZON",
                                "denominator_convention": "QUANTILE_RANK_POSITION",
                            },
                        )
                    )

            elif code == "RSK-07":  # Expected Shortfall (CVaR 95%)
                min_obs = int(params.get("min_observations", 1200 if horizon == "5Y" else (2400 if horizon == "10Y" else 2)))
                confidence_level = float(params.get("confidence_level", 0.95))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-07 ({horizon}): {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "confidence_level": confidence_level,
                            },
                        )
                    )
                else:
                    val = expected_shortfall.historical_expected_shortfall(fund_returns, confidence_level)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "confidence_level": confidence_level,
                                "observation_count": len(nav_values),
                                "return_count": len(fund_returns),
                                "min_observations_required": min_obs,
                                "annualization_convention": "NONE_1DAY_HORIZON",
                                "denominator_convention": "TAIL_OBSERVATION_COUNT",
                            },
                        )
                    )

            # MKT-01: Beta (Market Sensitivity)
            # REL-01 deprecated as an alias for Beta.
            elif code == "MKT-01":  # Beta (Excess-Return OLS)
                min_paired = max(default_min_obs, int(params.get("min_paired_observations", default_min_obs)))
                paired_count = len(bench_returns) if bench_returns else 0
                if not bench_returns or paired_count < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Beta.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon, "paired_count": paired_count, "min_paired_observations": min_paired},
                        )
                    )
                elif paired_count < min_paired:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient paired observations for Beta: {paired_count} provided, minimum {min_paired} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "paired_count": paired_count,
                                "min_paired_observations": min_paired,
                                "annualization": "NONE",
                            },
                        )
                    )
                elif not request.risk_free_series:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Risk-free rate series required for {code} standard beta.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "paired_count": paired_count,
                                "min_paired_observations": min_paired,
                                "risk_free_required": True,
                                "risk_free_aligned": False,
                            },
                        )
                    )
                elif request.risk_free_series and (aligned_rf_returns_relative is None and aligned_rf_returns is None):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Risk-free rate alignment failed for Beta: {rf_alignment_error}",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon, "risk_free_required": True, "risk_free_aligned": False, "error": rf_alignment_error},
                        )
                    )
                else:
                    rf_rates = aligned_rf_returns_relative if aligned_rf_returns_relative is not None else aligned_rf_returns
                    val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=rf_rates)
                    diag = {"methodology_status": "CANDIDATE", "requested_horizon": horizon, "risk_free_required": True, "risk_free_aligned": True, "risk_free_proxy": "FBIL_91D_TBILL", "annualization": "NONE", "paired_count": paired_count, "min_paired_observations": min_paired}
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics=diag,
                        )
                    )

            # MKT-02: Downside Beta 3Y
            # REL-04 deprecated as an alias for Downside Beta.
            elif code == "MKT-02":  # Downside Beta
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Downside Beta.",
                        )
                    )
                else:
                    downside_count = sum(1 for b in bench_returns if b < 0.0)
                    min_downside = int(params.get("min_downside_observations", 100))
                    if downside_count < min_downside or min_downside < 100:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=None,
                                units="RATIO",
                                status=CalculationStatus.INSUFFICIENT_DATA,
                                error_message=f"Insufficient downside observations: {downside_count} provided, minimum {max(100, min_downside)} required.",
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "convention_status": "M2N_07_APPROVED_METHODOLOGY_LEVEL",
                                    "downside_count": downside_count,
                                    "min_downside_observations": max(100, min_downside),
                                    "risk_free_required": False,
                                    "annualization": "NONE",
                                },
                            )
                        )
                    else:
                        val = downside_beta.downside_beta(aligned_fund_returns, bench_returns, min_downside_observations=min_downside)
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=val,
                                units="RATIO",
                                status=CalculationStatus.CALCULATED,
                                diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon, "downside_count": downside_count, "min_downside_observations": min_downside, "risk_free_required": False, "annualization": "NONE"},
                            )
                        )
            elif code == "REL-01":  # Tracking Error (Annualized Active Risk)
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Tracking Error.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon},
                        )
                    )
                else:
                    min_paired = int(params.get("min_paired_observations", default_min_obs))
                    total_paired = len(bench_returns)
                    if total_paired < min_paired:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=None,
                                units="PERCENTAGE",
                                status=CalculationStatus.INSUFFICIENT_DATA,
                                error_message=f"Insufficient paired observations for REL-01: {total_paired} provided, minimum {min_paired} required.",
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "paired_count": total_paired,
                                    "min_paired_observations": min_paired,
                                    "periods_per_year": periods_per_year,
                                    "annualization_convention": "SQRT_252",
                                    "denominator_convention": "SAMPLE_VARIANCE_N_MINUS_1",
                                },
                            )
                        )
                    else:
                        val = tracking_error.tracking_error(aligned_fund_returns, bench_returns, periods_per_year)
                        active_diffs = [p - b for p, b in zip(aligned_fund_returns, bench_returns)]
                        mean_active = sum(active_diffs) / len(active_diffs)
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=val,
                                units="PERCENTAGE",
                                status=CalculationStatus.CALCULATED,
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "paired_count": total_paired,
                                    "min_paired_observations": min_paired,
                                    "periods_per_year": periods_per_year,
                                    "mean_daily_active_return": mean_active,
                                    "annualized_mean_active_return": mean_active * periods_per_year,
                                    "annualization_convention": "SQRT_252",
                                    "denominator_convention": "SAMPLE_VARIANCE_N_MINUS_1",
                                },
                            )
                        )

            elif code == "REL-02":  # Jensen's Alpha (Daily Excess-Return OLS Intercept)
                if horizon in ("5Y", "10Y"):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"REL-02 is restricted for {horizon} horizon: multi-year CAPM excess-return stationarity not authorized under Phase 2H governance.",
                            diagnostics={
                                "methodology_status": "UNSUPPORTED",
                                "governance_status": "RESTRICTED",
                                "requested_horizon": horizon,
                                "limitation_code": "MULTI_YEAR_CAPM_STATIONARITY_UNAUTHORIZED",
                            },
                        )
                    )
                elif not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Benchmark returns required for Alpha.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon},
                        )
                    )
                else:
                    rf_rates = aligned_rf_returns_relative if aligned_rf_returns_relative is not None else aligned_rf_returns
                    b_val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=rf_rates)
                    val = alpha.jensens_alpha_ols(
                        aligned_fund_returns,
                        bench_returns,
                        risk_free_rates=rf_rates,
                        portfolio_beta=b_val,
                        periods_per_year=periods_per_year,
                    )
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "beta": b_val,
                                "periods_per_year": periods_per_year,
                                "formula": "OLS intercept (alpha_daily * 252)",
                            },
                        )
                    )

            elif code == "REL-03":  # Annualized Mean Active Return
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Annualized Mean Active Return.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon},
                        )
                    )
                else:
                    min_paired = int(params.get("min_paired_observations", default_min_obs))
                    total_paired = len(bench_returns)
                    if total_paired < min_paired:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=None,
                                units="PERCENTAGE",
                                status=CalculationStatus.INSUFFICIENT_DATA,
                                error_message=f"Insufficient paired observations for REL-03: {total_paired} provided, minimum {min_paired} required.",
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "paired_count": total_paired,
                                    "min_paired_observations": min_paired,
                                    "formula": "Mean of synchronous daily active returns (R_p - R_b) multiplied by periods_per_year",
                                    "annualization": "LINEAR_MULTIPLIER_252_ON_MEAN",
                                    "risk_free_required": False,
                                },
                            )
                        )
                    else:
                        val = active_return_mod.annualized_mean_active_return(
                            aligned_fund_returns, bench_returns, periods_per_year
                        )
                        mean_active = active_return_mod.mean_active_return(
                            aligned_fund_returns, bench_returns
                        )
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=val,
                                units="PERCENTAGE",
                                status=CalculationStatus.CALCULATED,
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "paired_count": total_paired,
                                    "min_paired_observations": min_paired,
                                    "mean_daily_active_return": mean_active,
                                    "periods_per_year": periods_per_year,
                                    "formula": "Mean of synchronous daily active returns (R_p - R_b) multiplied by periods_per_year",
                                    "annualization": "LINEAR_MULTIPLIER_252_ON_MEAN",
                                    "risk_free_required": False,
                                },
                            )
                        )

            elif code == "RAT-01":  # Sharpe Ratio
                if horizon in ("5Y", "10Y"):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"RAT-01 is restricted for {horizon} horizon: multi-year risk-free compounding and term structure alignment not authorized under Phase 2H governance.",
                            diagnostics={
                                "methodology_status": "UNSUPPORTED",
                                "governance_status": "RESTRICTED",
                                "requested_horizon": horizon,
                                "limitation_code": "MULTI_YEAR_RISK_FREE_ALIGNMENT_UNAUTHORIZED",
                            },
                        )
                    )
                elif request.risk_free_series and aligned_rf_returns is None:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Risk-free rate alignment failed: {rf_alignment_error}",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon, "risk_free_aligned": False, "error": rf_alignment_error},
                        )
                    )
                else:
                    rf_input = aligned_rf_returns if aligned_rf_returns is not None else rf_periodic
                    val = ratios.sharpe_ratio(fund_returns, rf_input, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "risk_free_aligned": aligned_rf_returns is not None,
                                "risk_free_proxy": "FBIL_91D_TBILL" if aligned_rf_returns is not None else "CONSTANT_SCALAR",
                                "periods_per_year": periods_per_year,
                                "annualization_convention": "SQRT_252_M2N_01",
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                                "convention_status": "M2N_01_M2N_02_APPROVED_METHODOLOGY_LEVEL",
                            },
                        )
                    )

            # RAT-03: Treynor Ratio 3Y (RAT-02 and RAT-05 aliases deprecated)
            elif code == "RAT-03":  # Treynor Ratio (3Y)
                if horizon in ("5Y", "10Y"):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"RAT-03 is restricted for {horizon} horizon: multi-year risk-free compounding not authorized under Phase 2H governance.",
                            diagnostics={
                                "methodology_status": "UNSUPPORTED",
                                "governance_status": "RESTRICTED",
                                "requested_horizon": horizon,
                                "limitation_code": "MULTI_YEAR_RISK_FREE_ALIGNMENT_UNAUTHORIZED",
                            },
                        )
                    )
                elif not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark returns required for Treynor Ratio beta denominator.",
                            diagnostics={"requested_horizon": horizon},
                        )
                    )
                elif request.risk_free_series and (aligned_rf_returns_relative is None and aligned_rf_returns is None):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Risk-free rate alignment failed for Treynor Ratio: {rf_alignment_error}",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon, "risk_free_aligned": False, "error": rf_alignment_error},
                        )
                    )
                else:
                    rf_input = aligned_rf_returns_relative if aligned_rf_returns_relative is not None else (aligned_rf_returns if aligned_rf_returns is not None else rf_periodic)
                    b_val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=rf_input if aligned_rf_returns is not None else None)
                    val = treynor.treynor_ratio(aligned_fund_returns, rf_input, b_val, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "beta": b_val,
                                "risk_free_aligned": aligned_rf_returns is not None,
                                "risk_free_proxy": "FBIL_91D_TBILL" if aligned_rf_returns is not None else "CONSTANT_SCALAR",
                                "periods_per_year": periods_per_year,
                                "annualization_convention": "252_MULTIPLIER_M2N_05",
                                "beta_convention": "EXCESS_RETURN_OLS_WITH_INTERCEPT_M2N_06",
                                "convention_status": "M2N_05_M2N_06_APPROVED_METHODOLOGY_LEVEL",
                                "methodology_status": "CANDIDATE",
                                "requested_horizon": horizon,
                            },
                        )
                    )

            elif code == "RAT-02":  # Sortino Ratio (Candidate with reconciled N-1 divisor)
                if horizon in ("5Y", "10Y"):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"RAT-02 is restricted for {horizon} horizon: multi-year MAR term adjustment not authorized under Phase 2H governance.",
                            diagnostics={
                                "methodology_status": "UNSUPPORTED",
                                "governance_status": "RESTRICTED",
                                "requested_horizon": horizon,
                                "limitation_code": "MULTI_YEAR_MAR_ALIGNMENT_UNAUTHORIZED",
                            },
                        )
                    )
                else:
                    divisor_conv = params.get("divisor_convention", "N")
                    # Compute reconciled N-1 sample downside semideviation
                    downside_semidev = semideviation.downside_semideviation(
                        fund_returns, target_return=rf_periodic, periods_per_year=periods_per_year
                    )
                    ann_excess_ret = (sum(r - rf_periodic for r in fund_returns) / len(fund_returns)) * periods_per_year
                    sortino_reconciled = (ann_excess_ret / downside_semidev) if downside_semidev > 0 else None

                    if divisor_conv in ("N_MINUS_1", "SAMPLE"):
                        if downside_semidev == 0:
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=None,
                                    units="RATIO",
                                    status=CalculationStatus.ERROR,
                                    error_message="Sortino ratio is undefined when downside semideviation is zero.",
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "target_return_mar": rf_periodic,
                                        "downside_deviation": 0.0,
                                        "convention": "downside_semideviation_divisor_N_MINUS_1_RECONCILED",
                                    },
                                )
                            )
                        else:
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=sortino_reconciled,
                                    units="RATIO",
                                    status=CalculationStatus.CALCULATED,
                                    diagnostics={
                                        "convention": "downside_semideviation_divisor_N_MINUS_1_RECONCILED",
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "target_return_mar": rf_periodic,
                                        "downside_deviation": downside_semidev,
                                        "annualized_excess_return": ann_excess_ret,
                                        "annualization_factor": periods_per_year,
                                    },
                                )
                            )
                    else:
                        val = ratios.sortino_ratio(fund_returns, rf_periodic, periods_per_year=periods_per_year)
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=val,
                                units="RATIO",
                                status=CalculationStatus.CALCULATED,
                                diagnostics={
                                    "convention": "downside_deviation_divisor_N",
                                    "methodology_status": "CANDIDATE_UNRECONCILED_DIVISOR_CONFLICT",
                                    "requested_horizon": horizon,
                                    "warning": "ratios.py uses statistics.downside_deviation (N divisor) which conflicts with semideviation.py (N-1 divisor). Unapproved candidate convention.",
                                    "reconciled_n_minus_one_downside_deviation": downside_semidev,
                                    "reconciled_n_minus_one_sortino": sortino_reconciled,
                                    "reconciled_divisor_convention": "N_MINUS_1_SAMPLE",
                                },
                            )
                        )

            elif code == "RAT-04":  # Information Ratio (Annualized active return over tracking error)
                if horizon in ("5Y", "10Y"):
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            numeric_value=None,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"RAT-04 is restricted for {horizon} horizon: multi-cycle benchmark synchronization validation not yet completed.",
                            diagnostics={
                                "methodology_status": "UNSUPPORTED",
                                "governance_status": "RESTRICTED",
                                "requested_horizon": horizon,
                                "limitation_code": "MULTI_YEAR_BENCHMARK_SYNC_UNVALIDATED",
                            },
                        )
                    )
                elif not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark returns required for Information Ratio.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon},
                        )
                    )
                else:
                    min_paired = int(params.get("min_paired_observations", default_min_obs))
                    total_paired = len(bench_returns)
                    if total_paired < min_paired:
                        results.append(
                            MetricOutputItem(
                                metric_code=code,
                                period_type=horizon,
                                numeric_value=None,
                                units="RATIO",
                                status=CalculationStatus.INSUFFICIENT_DATA,
                                error_message=f"Insufficient paired observations for RAT-04: {total_paired} provided, minimum {min_paired} required.",
                                diagnostics={
                                    "methodology_status": "CANDIDATE",
                                    "requested_horizon": horizon,
                                    "paired_count": total_paired,
                                    "min_paired_observations": min_paired,
                                    "periods_per_year": periods_per_year,
                                    "annualization_convention": "SQRT_252_ON_DAILY_MEAN_OVER_TE",
                                    "denominator_convention": "SAMPLE_TRACKING_ERROR_N_MINUS_1",
                                },
                            )
                        )
                    else:
                        active_diffs = [p - b for p, b in zip(aligned_fund_returns, bench_returns)]
                        mean_active = sum(active_diffs) / len(active_diffs)
                        var_active = sum((d - mean_active) ** 2 for d in active_diffs) / (len(active_diffs) - 1)
                        te_daily = math.sqrt(var_active)

                        if math.isclose(te_daily, 0.0, abs_tol=1e-15):
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=None,
                                    units="RATIO",
                                    status=CalculationStatus.ERROR,
                                    error_message="Zero tracking error: active returns have zero sample variance against benchmark; Information Ratio denominator is zero.",
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "paired_count": total_paired,
                                        "min_paired_observations": min_paired,
                                        "zero_tracking_error": True,
                                        "mean_daily_active_return": mean_active,
                                    },
                                )
                            )
                        else:
                            val = information_ratio.information_ratio(aligned_fund_returns, bench_returns, periods_per_year)
                            te_annual = te_daily * math.sqrt(periods_per_year)
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=val,
                                    units="RATIO",
                                    status=CalculationStatus.CALCULATED,
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "paired_count": total_paired,
                                        "min_paired_observations": min_paired,
                                        "periods_per_year": periods_per_year,
                                        "mean_daily_active_return": mean_active,
                                        "annualized_mean_active_return": mean_active * periods_per_year,
                                        "annualized_tracking_error": te_annual,
                                        "annualization_convention": "SQRT_252_ON_DAILY_MEAN_OVER_TE",
                                        "denominator_convention": "SAMPLE_TRACKING_ERROR_N_MINUS_1",
                                    },
                                )
                            )

            elif code in ("MKT-03", "MKT-04", "MKT-05"):
                if not bench_returns or len(bench_returns) < 2 or not aligned_fund_returns:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type=horizon,
                            units="PERCENTAGE" if code in ("MKT-03", "MKT-04") else "PERCENTAGE_POINTS",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Synchronous aligned benchmark return series required for {code}.",
                            diagnostics={"methodology_status": "CANDIDATE", "requested_horizon": horizon},
                        )
                    )
                else:
                    min_up = int(params.get("min_upside_observations", 150 if horizon == "3Y" else (250 if horizon == "5Y" else 500)))
                    min_down = int(params.get("min_downside_observations", 100 if horizon == "3Y" else (180 if horizon == "5Y" else 360)))
                    cap_metrics = capture.compute_capture_metrics(
                        portfolio_returns=aligned_fund_returns,
                        benchmark_returns=bench_returns,
                        min_up_days=min_up,
                        min_down_days=min_down,
                    )
                    if code == "MKT-03":  # Upside Capture Ratio
                        if cap_metrics["upside_status"] != "CALCULATED":
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=None,
                                    units="PERCENTAGE",
                                    status=CalculationStatus.INSUFFICIENT_DATA if cap_metrics["upside_status"] == "INSUFFICIENT_DATA" else CalculationStatus.ERROR,
                                    error_message=cap_metrics["upside_error"],
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "up_days_count": cap_metrics["up_days_count"],
                                        "min_up_days_required": min_up,
                                        "total_paired_days": cap_metrics["total_paired_days"],
                                        "fund_up_cumulative": cap_metrics["fund_up_cumulative"],
                                        "bench_up_cumulative": cap_metrics["bench_up_cumulative"],
                                        "annualization_convention": "UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE",
                                        "denominator_convention": "CUMULATIVE_BENCHMARK_UPSIDE_GT_ZERO",
                                        "asymmetry_disclaimer": "Capture ratios describe historical benchmark participation over observed periods. A down-capture < 100% does not guarantee downside protection in future market declines.",
                                    },
                                )
                            )
                        else:
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=cap_metrics["upside_capture"],
                                    units="PERCENTAGE",
                                    status=CalculationStatus.CALCULATED,
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "up_days_count": cap_metrics["up_days_count"],
                                        "min_up_days_required": min_up,
                                        "total_paired_days": cap_metrics["total_paired_days"],
                                        "fund_up_cumulative": cap_metrics["fund_up_cumulative"],
                                        "bench_up_cumulative": cap_metrics["bench_up_cumulative"],
                                        "annualization_convention": "UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE",
                                        "denominator_convention": "CUMULATIVE_BENCHMARK_UPSIDE_GT_ZERO",
                                        "asymmetry_disclaimer": "Capture ratios describe historical benchmark participation over observed periods. A down-capture < 100% does not guarantee downside protection in future market declines.",
                                    },
                                )
                            )

                    elif code == "MKT-04":  # Downside Capture Ratio
                        if cap_metrics["downside_status"] != "CALCULATED":
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=None,
                                    units="PERCENTAGE",
                                    status=CalculationStatus.INSUFFICIENT_DATA if cap_metrics["downside_status"] == "INSUFFICIENT_DATA" else CalculationStatus.ERROR,
                                    error_message=cap_metrics["downside_error"],
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "down_days_count": cap_metrics["down_days_count"],
                                        "min_down_days_required": min_down,
                                        "total_paired_days": cap_metrics["total_paired_days"],
                                        "fund_down_cumulative": cap_metrics["fund_down_cumulative"],
                                        "bench_down_cumulative": cap_metrics["bench_down_cumulative"],
                                        "inverse_capture_gain": cap_metrics["inverse_capture_gain"],
                                        "annualization_convention": "UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE",
                                        "denominator_convention": "CUMULATIVE_BENCHMARK_DOWNSIDE_LT_ZERO",
                                        "asymmetry_disclaimer": "Capture ratios describe historical benchmark participation over observed periods. A down-capture < 100% does not guarantee downside protection in future market declines.",
                                    },
                                )
                            )
                        else:
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=cap_metrics["downside_capture"],
                                    units="PERCENTAGE",
                                    status=CalculationStatus.CALCULATED,
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "down_days_count": cap_metrics["down_days_count"],
                                        "min_down_days_required": min_down,
                                        "total_paired_days": cap_metrics["total_paired_days"],
                                        "fund_down_cumulative": cap_metrics["fund_down_cumulative"],
                                        "bench_down_cumulative": cap_metrics["bench_down_cumulative"],
                                        "inverse_capture_gain": cap_metrics["inverse_capture_gain"],
                                        "annualization_convention": "UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE",
                                        "denominator_convention": "CUMULATIVE_BENCHMARK_DOWNSIDE_LT_ZERO",
                                        "asymmetry_disclaimer": "Capture ratios describe historical benchmark participation over observed periods. A down-capture < 100% does not guarantee downside protection in future market declines.",
                                    },
                                )
                            )

                    elif code == "MKT-05":  # Capture Spread
                        if cap_metrics["spread_status"] != "CALCULATED":
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=None,
                                    units="PERCENTAGE_POINTS",
                                    status=CalculationStatus.INSUFFICIENT_DATA,
                                    error_message=cap_metrics["spread_error"],
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "upside_capture": cap_metrics["upside_capture"],
                                        "downside_capture": cap_metrics["downside_capture"],
                                        "annualization_convention": "NONE_DIFFERENCE_BETWEEN_PERCENTAGES",
                                    },
                                )
                            )
                        else:
                            results.append(
                                MetricOutputItem(
                                    metric_code=code,
                                    period_type=horizon,
                                    numeric_value=cap_metrics["capture_spread"],
                                    units="PERCENTAGE_POINTS",
                                    status=CalculationStatus.CALCULATED,
                                    diagnostics={
                                        "methodology_status": "CANDIDATE",
                                        "requested_horizon": horizon,
                                        "upside_capture": cap_metrics["upside_capture"],
                                        "downside_capture": cap_metrics["downside_capture"],
                                        "annualization_convention": "NONE_DIFFERENCE_BETWEEN_PERCENTAGES",
                                    },
                                )
                            )

            else:
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        units="UNDEFINED",
                        status=CalculationStatus.ERROR,
                        error_message=f"Unsupported or unmapped metric code: {code}",
                    )
                )

        except Exception as e:
            results.append(
                MetricOutputItem(
                    metric_code=code,
                    units="UNDEFINED",
                    status=CalculationStatus.ERROR,
                    error_message=f"Calculation exception: {str(e)}",
                )
            )

    return results
