from __future__ import annotations

import datetime
import math
from typing import Any, Dict, List, Optional, Tuple

from src.api.models import CalculationRequest, CalculationStatus, MetricOutputItem
from src import (
    alpha,
    beta,
    expected_shortfall,
    information_ratio,
    ratios,
    returns as ret_mod,
    risk,
    statistics,
    tracking_error,
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
            if code == "RET-01":  # CAGR
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

            elif code == "RSK-01":  # Annualized Volatility
                val = statistics.volatility(fund_returns, periods_per_year)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"methodology_status": "CANDIDATE", "periods_per_year": periods_per_year},
                    )
                )

            elif code == "RSK-02":  # Historical VaR 95%
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

            elif code == "RSK-03":  # Parametric VaR 95%
                val = var.parametric_var(fund_returns, 0.95)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"confidence_level": 0.95, "methodology_status": "CANDIDATE"},
                    )
                )

            elif code == "RSK-04":  # Expected Shortfall (CVaR 95%)
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

            elif code == "RSK-05":  # Maximum Drawdown
                val = risk.maximum_drawdown(nav_values)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="PERCENTAGE",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"methodology_status": "CANDIDATE"},
                    )
                )

            elif code == "RSK-06":  # Ulcer Index
                val = ulcer_index.ulcer_index(nav_values)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="RATIO",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"methodology_status": "CANDIDATE"},
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
                    val = beta.beta(aligned_fund_returns, bench_returns)
                    results.append(
                        MetricOutputItem(
                            metric_code=code,
                            numeric_value=val,
                            units="RATIO",
                            status=CalculationStatus.CALCULATED,
                            diagnostics={"methodology_status": "CANDIDATE"},
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
                    b_val = beta.beta(aligned_fund_returns, bench_returns)
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
                val = ratios.sharpe_ratio(fund_returns, rf_periodic, periods_per_year)
                results.append(
                    MetricOutputItem(
                        metric_code=code,
                        numeric_value=val,
                        units="RATIO",
                        status=CalculationStatus.CALCULATED,
                        diagnostics={"risk_free_rate": rf_annual, "periods_per_year": periods_per_year, "methodology_status": "CANDIDATE"},
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
