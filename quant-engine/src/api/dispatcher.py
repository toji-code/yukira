from __future__ import annotations

import datetime
import math
from typing import Any, Dict, List, Optional, Tuple

from src.api.models import CalculationRequest, CalculationStatus, MetricOutputItem
from src import (
    alpha,
    beta,
    downside_beta,
    expected_shortfall,
    information_ratio,
    ratios,
    returns as ret_mod,
    risk,
    risk_free,
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
    if request.benchmark_series and len(request.benchmark_series) >= 2:
        sorted_bench = sorted(request.benchmark_series, key=lambda x: x.effective_date)
        bench_map = {obs.effective_date: obs.value for obs in sorted_bench}
        aligned_nav = []
        aligned_bench = []
        for d, v in zip(dates, nav_values):
            if d in bench_map:
                aligned_nav.append(v)
                aligned_bench.append(bench_map[d])
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
    rf_annual = float(params.get("risk_free_rate", 0.065))  # Candidate 6.5% default
    periods_per_year = float(params.get("periods_per_year", 252.0))
    rf_periodic = rf_annual / periods_per_year

    # Handle optional risk-free series
    aligned_rf_returns: Optional[List[float]] = None
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
        except Exception:
            aligned_rf_returns = None

    # Elapsed years for CAGR
    try:
        d_start = datetime.date.fromisoformat(dates[0])
        d_end = datetime.date.fromisoformat(dates[-1])
        elapsed_days = (d_end - d_start).days
        elapsed_years = max(elapsed_days / 365.25, 1.0 / periods_per_year)
    except Exception:
        elapsed_years = len(fund_returns) / periods_per_year

    for code in request.metric_codes:
        try:
            if code in ("RET-01", "RET-03", "RET-04"):  # CAGR metrics
                val = ret_mod.cagr(nav_values[0], nav_values[-1], elapsed_years)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"methodology_status": "CANDIDATE", "elapsed_years": elapsed_years},
                    )
                )

            elif code == "RET-02":  # Total Return
                val = ret_mod.period_return(nav_values[0], nav_values[-1])
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"methodology_status": "CANDIDATE"},
                    )
                )

            elif code == "RSK-01":  # Annualized Volatility (3Y)
                min_obs = int(params.get("min_observations", 2))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-01: {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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
                            period_type="3Y",
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "periods_per_year": periods_per_year,
                                "annualization_convention": "SQRT_252_CANDIDATE",
                                "denominator_convention": "N_MINUS_ONE_CANDIDATE",
                                "observation_count": len(nav_values),
                                "return_count": len(fund_returns),
                                "sample_std_daily": statistics.stdev(fund_returns) if len(fund_returns) >= 2 else 0.0,
                            },
                        )
                    )

            elif code == "RSK-02":  # Downside Semideviation (3Y)
                min_obs = int(params.get("min_observations", 2))
                target_return = float(params.get("target_return", 0.0))
                if len(nav_values) < min_obs or len(fund_returns) < 1:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-02: {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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
                            period_type="3Y",
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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

            elif code == "RSK-03":  # 3-Year Maximum Drawdown
                min_obs = int(params.get("min_observations", 2))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=None,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-03: {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                            },
                        )
                    )
                else:
                    details = risk.drawdown_details(nav_values, dates)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=details["max_drawdown"],
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "peak_nav": details["peak_nav"],
                                "trough_nav": details["trough_nav"],
                                "peak_date": details["peak_date"],
                                "trough_date": details["trough_date"],
                                "running_peak_denominator_convention": "RUNNING_PEAK_NAV_CANDIDATE",
                            },
                        )
                    )

            elif code == "RSK-04":  # Maximum Drawdown Duration
                min_obs = int(params.get("min_observations", 2))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=None,
                            units="DAYS",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-04: {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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
                            period_type="3Y",
                            numeric_value=float(duration_info["max_duration_calendar_days"]),
                            units="DAYS",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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

            elif code == "RSK-05":  # Ulcer Index (3Y)
                min_obs = int(params.get("min_observations", 2))
                if len(nav_values) < min_obs:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            period_type="3Y",
                            numeric_value=None,
                            units="POINTS",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message=f"Insufficient observations for RSK-05: {len(nav_values)} provided, minimum {min_obs} required.",
                            diagnostics={
                                "methodology_status": "CANDIDATE",
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
                            period_type="3Y",
                            numeric_value=val,
                            units="POINTS",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={
                                "methodology_status": "CANDIDATE",
                                "observation_count": len(nav_values),
                                "min_observations_required": min_obs,
                                "denominator_convention": "N_OBSERVATIONS_CANDIDATE",
                            },
                        )
                    )

            elif code == "RSK-06":  # Historical VaR 95%
                val = var.historical_var(fund_returns, 0.95)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"confidence_level": 0.95, "methodology_status": "CANDIDATE"},
                    )
                )

            elif code == "RSK-07":  # Expected Shortfall (CVaR 95%)
                val = expected_shortfall.historical_expected_shortfall(fund_returns, 0.95)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"confidence_level": 0.95, "methodology_status": "CANDIDATE"},
                    )
                )

            elif code == "REL-01":  # Beta
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Beta.",
                        )
                    )
                else:
                    if aligned_rf_returns is not None:
                        val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=aligned_rf_returns)
                        diag = {"methodology_status": "APPROVED", "risk_free_aligned": True, "risk_free_proxy": "FBIL_91D_TBILL"}
                    else:
                        val = beta.beta(aligned_fund_returns, bench_returns)
                        diag = {"methodology_status": "APPROVED", "risk_free_aligned": False}
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics=diag,
                        )
                    )

            elif code == "REL-04":  # Downside Beta
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Downside Beta.",
                        )
                    )
                else:
                    min_downside = int(params.get("min_downside_observations", 2))
                    val = downside_beta.downside_beta(aligned_fund_returns, bench_returns, min_downside_observations=min_downside)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"methodology_status": "APPROVED", "min_downside_observations": min_downside, "risk_free_required": False},
                        )
                    )

            elif code == "REL-02":  # Tracking Error
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark return series required for Tracking Error.",
                        )
                    )
                else:
                    val = tracking_error.tracking_error(aligned_fund_returns, bench_returns, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"periods_per_year": periods_per_year, "methodology_status": "CANDIDATE"},
                        )
                    )

            elif code == "REL-03":  # Jensen's Alpha
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="PERCENTAGE",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Benchmark returns required for Alpha.",
                        )
                    )
                else:
                    b_val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=aligned_rf_returns)
                    f_ret = ret_mod.period_return(nav_values[0], nav_values[-1])
                    b_ret = ret_mod.period_return(bench_values[0], bench_values[-1])
                    val = alpha.jensens_alpha(f_ret, b_ret, rf_annual, b_val)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="PERCENTAGE",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"risk_free_rate": rf_annual, "beta": b_val, "methodology_status": "CANDIDATE"},
                        )
                    )

            elif code == "RAT-01":  # Sharpe Ratio
                rf_input = aligned_rf_returns if aligned_rf_returns is not None else rf_periodic
                val = ratios.sharpe_ratio(fund_returns, rf_input, periods_per_year)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="RATIO",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"risk_free_aligned": aligned_rf_returns is not None, "periods_per_year": periods_per_year, "methodology_status": "APPROVED"},
                    )
                )

            elif code in ("RAT-02", "RAT-05"):  # Treynor Ratio
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark returns required for Treynor Ratio beta denominator.",
                        )
                    )
                else:
                    rf_input = aligned_rf_returns if aligned_rf_returns is not None else rf_periodic
                    b_val = beta.beta(aligned_fund_returns, bench_returns, risk_free_rates=aligned_rf_returns)
                    val = treynor.treynor_ratio(aligned_fund_returns, rf_input, b_val, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"beta": b_val, "risk_free_aligned": aligned_rf_returns is not None, "periods_per_year": periods_per_year, "methodology_status": "APPROVED"},
                        )
                    )

            elif code == "RAT-03":  # Sortino Ratio (Candidate with N vs N-1 divisor flag)
                val = ratios.sortino_ratio(fund_returns, rf_periodic, periods_per_year=periods_per_year)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="RATIO",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={
                            "convention": "downside_deviation_divisor_N",
                            "methodology_status": "CANDIDATE_UNRECONCILED_DIVISOR_CONFLICT",
                            "warning": "ratios.py uses statistics.downside_deviation (N divisor) which conflicts with semideviation.py (N-1 divisor). Unapproved candidate convention.",
                        },
                    )
                )

            elif code == "RAT-04":  # Information Ratio
                if not bench_returns or len(bench_returns) < 2:
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            units="RATIO",
                            status=CalculationStatus.INSUFFICIENT_DATA,
                            error_message="Aligned benchmark returns required for Information Ratio.",
                        )
                    )
                else:
                    val = information_ratio.information_ratio(aligned_fund_returns, bench_returns, periods_per_year)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"periods_per_year": periods_per_year, "methodology_status": "CANDIDATE"},
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
