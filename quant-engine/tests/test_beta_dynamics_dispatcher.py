"""
YUKIRA Benchmark Beta Dynamics Dispatcher Tests (§REL-01 / §REL-04 / §REL-05).

Covers the dispatcher contract for the beta trio vertical slice:
1. Standard Beta (REL-01): excess-return OLS, >= 700 paired days, risk-free aligned.
2. Downside Beta (REL-04): conditioned on R_b < 0, >= 100 down-days, no risk-free.
3. Upside Beta (REL-05): conditioned on R_b > 0, >= 150 up-days, no risk-free.
4. Deterministic INSUFFICIENT_DATA (numeric_value is never zero or fabricated).
5. Exact numerical equivalence with the existing pure kernels (no duplicated math).
"""

from __future__ import annotations

import datetime
import math

from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem
from src import beta as beta_mod
from src import downside_beta as dbeta_mod
from src import upside_beta as ubeta_mod
from src import statistics as stats_mod
from src.risk_free import RiskFreeObservation, align_risk_free_series

TRIO = ["REL-01", "REL-04", "REL-05"]
START = datetime.date(2021, 1, 15)


def _dates(n: int) -> list[str]:
    return [(START + datetime.timedelta(days=i)).isoformat() for i in range(n)]


def _oscillating_bench_returns(n: int) -> list[float]:
    """Deterministic up/down benchmark return path with zero-mean oscillation."""
    return [0.004 * math.sin(i * 0.37) + 0.003 * math.cos(i * 0.11) for i in range(n - 1)]


def _build_series(n: int, bench_mode: str = "oscillating"):
    """Build synchronous NAV and benchmark series of length n."""
    dates = _dates(n)
    if bench_mode == "oscillating":
        b_rets = _oscillating_bench_returns(n)
    elif bench_mode == "increasing":
        b_rets = [0.0015 + 0.0002 * math.sin(i * 0.7) for i in range(n - 1)]
    elif bench_mode == "decreasing":
        b_rets = [-0.0015 + 0.0002 * math.sin(i * 0.7) for i in range(n - 1)]
    else:
        raise ValueError(bench_mode)

    # Fund tracks the benchmark with a deterministic idiosyncratic component.
    p_rets = [0.92 * b + 0.0015 * math.sin(i * 0.53) for i, b in enumerate(b_rets)]

    bench_values, nav_values = [1000.0], [100.0]
    for b, p in zip(b_rets, p_rets):
        bench_values.append(bench_values[-1] * (1.0 + b))
        nav_values.append(nav_values[-1] * (1.0 + p))

    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, nav_values)
    ]
    bench_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, bench_values)
    ]
    return dates, nav_series, bench_series


def _request(nav_series, bench_series, metric_codes=None, **kwargs) -> CalculationRequest:
    as_of = nav_series[-1].effective_date
    if "risk_free_series" not in kwargs:
        kwargs["risk_free_series"] = [
            ObservationItem(
                effective_date=o.effective_date,
                value=0.0650,
                availability_time=o.availability_time,
                revision_seq=o.revision_seq,
            )
            for o in nav_series
        ]
    return CalculationRequest(
        request_id="REQ-BETA-DISPATCH",
        as_of_date=as_of,
        knowledge_cutoff_time=f"{as_of}T23:59:59+05:30",
        metric_codes=metric_codes or TRIO,
        nav_series=nav_series,
        benchmark_series=bench_series,
        **kwargs,
    )


def test_trio_calculates_and_matches_pure_kernels_exactly():
    dates, nav_series, bench_series = _build_series(740)

    items = dispatch_calculation(_request(nav_series, bench_series))
    assert len(items) == 3
    code_map = {i.metric_code: i for i in items}

    for code in TRIO:
        assert code_map[code].status == CalculationStatus.CALCULATED, code
        assert code_map[code].numeric_value is not None, code
        assert code_map[code].units == "RATIO"
        assert code_map[code].period_type == "3Y"

    nav_values = [o.value for o in nav_series]
    bench_values = [o.value for o in bench_series]
    fund_returns = stats_mod.periodic_returns(nav_values)
    bench_returns = stats_mod.periodic_returns(bench_values)

    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date.fromisoformat(o.effective_date),
            quoted_yield=0.0650,
            availability_time=datetime.datetime.fromisoformat(o.availability_time),
            revision_seq=o.revision_seq,
        )
        for o in nav_series
    ]
    aligned_rf = align_risk_free_series(
        observation_dates=dates,
        risk_free_observations=rf_obs,
        analysis_cutoff=dates[-1],
        knowledge_cutoff=f"{dates[-1]}T23:59:59+05:30",
        max_lookback_days=4,
    )
    expected_std = beta_mod.beta(fund_returns, bench_returns, risk_free_rates=aligned_rf)
    expected_down = dbeta_mod.downside_beta(fund_returns, bench_returns)
    expected_up = ubeta_mod.upside_beta(fund_returns, bench_returns)

    assert abs(code_map["REL-01"].numeric_value - expected_std) < 1e-12
    assert abs(code_map["REL-04"].numeric_value - expected_down) < 1e-12
    assert abs(code_map["REL-05"].numeric_value - expected_up) < 1e-12

    # Regime betas must differ from each other on an oscillating path.
    assert not math.isclose(expected_down, expected_up, abs_tol=1e-9)

    # Diagnostics contract
    d01 = code_map["REL-01"].diagnostics
    d04 = code_map["REL-04"].diagnostics
    d05 = code_map["REL-05"].diagnostics
    assert d01["paired_count"] == 739
    assert d01["min_paired_observations"] == 700
    assert d01["annualization"] == "NONE"
    assert d04["risk_free_required"] is False
    assert d05["risk_free_required"] is False
    assert d04["downside_count"] + d05["upside_count"] <= 739

    # Governance disclosure: implemented beta metrics remain candidate until empirical validation.
    assert d01["methodology_status"] == "CANDIDATE"
    assert d04["methodology_status"] == "CANDIDATE"
    assert d05["methodology_status"] == "CANDIDATE"


def test_standard_beta_insufficient_below_700_paired_days():
    _, nav_series, bench_series = _build_series(650)

    items = dispatch_calculation(_request(nav_series, bench_series))
    code_map = {i.metric_code: i for i in items}

    rel01 = code_map["REL-01"]
    assert rel01.status == CalculationStatus.INSUFFICIENT_DATA
    assert rel01.numeric_value is None
    assert "minimum 700 required" in rel01.error_message
    assert rel01.diagnostics["paired_count"] == 649
    assert rel01.diagnostics["min_paired_observations"] == 700

    # Conditioned betas still evaluated on their own thresholds.
    assert code_map["REL-04"].status == CalculationStatus.CALCULATED
    assert code_map["REL-05"].status == CalculationStatus.CALCULATED


def test_downside_beta_insufficient_when_no_benchmark_down_days():
    _, nav_series, bench_series = _build_series(740, bench_mode="increasing")

    items = dispatch_calculation(_request(nav_series, bench_series))
    code_map = {i.metric_code: i for i in items}

    rel04 = code_map["REL-04"]
    assert rel04.status == CalculationStatus.INSUFFICIENT_DATA
    assert rel04.numeric_value is None
    assert rel04.diagnostics["downside_count"] == 0
    assert rel04.diagnostics["min_downside_observations"] == 100
    assert "minimum 100 required" in rel04.error_message

    assert code_map["REL-01"].status == CalculationStatus.CALCULATED
    assert code_map["REL-05"].status == CalculationStatus.CALCULATED


def test_upside_beta_insufficient_when_no_benchmark_up_days():
    _, nav_series, bench_series = _build_series(740, bench_mode="decreasing")

    items = dispatch_calculation(_request(nav_series, bench_series))
    code_map = {i.metric_code: i for i in items}

    rel05 = code_map["REL-05"]
    assert rel05.status == CalculationStatus.INSUFFICIENT_DATA
    assert rel05.numeric_value is None
    assert rel05.diagnostics["upside_count"] == 0
    assert rel05.diagnostics["min_upside_observations"] == 150
    assert "minimum 150 required" in rel05.error_message

    assert code_map["REL-01"].status == CalculationStatus.CALCULATED
    assert code_map["REL-04"].status == CalculationStatus.CALCULATED


def test_conditioned_threshold_floors_cannot_be_loosened_by_parameters():
    _, nav_series, bench_series = _build_series(740)

    # Attempt to weaken thresholds below the approved floors via parameters.
    req = _request(
        nav_series,
        bench_series,
        metric_codes=["REL-01", "REL-04", "REL-05"],
        parameters={
            "min_paired_observations": 10,
            "min_downside_observations": 5,
            "min_upside_observations": 50,
        },
    )
    items = dispatch_calculation(req)
    code_map = {i.metric_code: i for i in items}

    assert code_map["REL-01"].diagnostics["min_paired_observations"] == 700

    # Up-day subsample is deliberately small: 20 oscillating up-days only.
    small_dates = _dates(30)
    small_nav = [
        ObservationItem(effective_date=d, value=100.0 + i, availability_time=f"{d}T18:00:00+05:30")
        for i, d in enumerate(small_dates)
    ]
    small_bench = [
        ObservationItem(effective_date=d, value=1000.0 + i, availability_time=f"{d}T18:00:00+05:30")
        for i, d in enumerate(small_dates)
    ]
    small = dispatch_calculation(
        _request(small_nav, small_bench, parameters={"min_upside_observations": 50, "min_downside_observations": 5})
    )
    small_map = {i.metric_code: i for i in small}
    # Upside floor of 150 cannot be lowered to 50.
    assert small_map["REL-05"].status == CalculationStatus.INSUFFICIENT_DATA
    assert small_map["REL-05"].diagnostics["min_upside_observations"] == 150
    # Downside floor of 100 cannot be lowered to 5.
    assert small_map["REL-04"].status == CalculationStatus.INSUFFICIENT_DATA
    assert small_map["REL-04"].diagnostics["min_downside_observations"] == 100


def test_missing_benchmark_series_marks_all_three_insufficient():
    _, nav_series, _ = _build_series(740)

    req = CalculationRequest(
        request_id="REQ-BETA-NO-BENCH",
        as_of_date=nav_series[-1].effective_date,
        knowledge_cutoff_time=f"{nav_series[-1].effective_date}T23:59:59+05:30",
        metric_codes=TRIO,
        nav_series=nav_series,
        risk_free_series=[
            ObservationItem(effective_date=o.effective_date, value=0.0650, availability_time=o.availability_time)
            for o in nav_series
        ],
    )
    items = dispatch_calculation(req)
    assert len(items) == 3
    for item in items:
        assert item.status == CalculationStatus.INSUFFICIENT_DATA
        assert item.numeric_value is None
        assert "Aligned benchmark return series required" in item.error_message


def test_missing_risk_free_series_marks_standard_beta_insufficient_only():
    _, nav_series, bench_series = _build_series(740)

    items = dispatch_calculation(_request(nav_series, bench_series, risk_free_series=[]))
    code_map = {i.metric_code: i for i in items}

    rel01 = code_map["REL-01"]
    assert rel01.status == CalculationStatus.INSUFFICIENT_DATA
    assert rel01.numeric_value is None
    assert "Risk-free rate series required" in rel01.error_message
    assert rel01.diagnostics["risk_free_required"] is True
    assert rel01.diagnostics["risk_free_aligned"] is False

    assert code_map["REL-04"].status == CalculationStatus.CALCULATED
    assert code_map["REL-05"].status == CalculationStatus.CALCULATED


def test_risk_free_alignment_failure_isolated_to_standard_beta():
    dates, nav_series, bench_series = _build_series(740)
    # Risk-free quote only exists at the start; gap far exceeds the 4-day lookback.
    rf_series = [
        ObservationItem(effective_date=dates[0], value=0.0695, availability_time=f"{dates[0]}T18:00:00+05:30")
    ]

    items = dispatch_calculation(_request(nav_series, bench_series, risk_free_series=rf_series))
    code_map = {i.metric_code: i for i in items}

    rel01 = code_map["REL-01"]
    assert rel01.status == CalculationStatus.INSUFFICIENT_DATA
    assert rel01.numeric_value is None
    assert "Risk-free rate alignment failed" in rel01.error_message
    assert rel01.diagnostics["risk_free_aligned"] is False

    # Downside/Upside beta have zero risk-free dependency and remain calculable.
    assert code_map["REL-04"].status == CalculationStatus.CALCULATED
    assert code_map["REL-05"].status == CalculationStatus.CALCULATED
    assert code_map["REL-04"].diagnostics["risk_free_required"] is False
    assert code_map["REL-05"].diagnostics["risk_free_required"] is False


def test_standard_beta_with_aligned_fbil_series_matches_kernel():
    dates, nav_series, bench_series = _build_series(740)
    rf_series = [
        ObservationItem(effective_date=d, value=0.0650, availability_time=f"{d}T18:00:00+05:30")
        for d in dates
    ]

    items = dispatch_calculation(_request(nav_series, bench_series, risk_free_series=rf_series))
    code_map = {i.metric_code: i for i in items}

    rel01 = code_map["REL-01"]
    assert rel01.status == CalculationStatus.CALCULATED
    assert rel01.diagnostics["risk_free_aligned"] is True
    assert rel01.diagnostics["risk_free_proxy"] == "FBIL_91D_TBILL"

    nav_values = [o.value for o in nav_series]
    bench_values = [o.value for o in bench_series]
    fund_returns = stats_mod.periodic_returns(nav_values)
    bench_returns = stats_mod.periodic_returns(bench_values)
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date.fromisoformat(o.effective_date),
            quoted_yield=o.value,
            availability_time=datetime.datetime.fromisoformat(o.availability_time),
            revision_seq=o.revision_seq,
        )
        for o in rf_series
    ]
    aligned_rf = align_risk_free_series(
        observation_dates=dates,
        risk_free_observations=rf_obs,
        analysis_cutoff=dates[-1],
        knowledge_cutoff=f"{dates[-1]}T23:59:59+05:30",
        max_lookback_days=4,
    )
    expected = beta_mod.beta(fund_returns, bench_returns, risk_free_rates=aligned_rf)
    assert abs(rel01.numeric_value - expected) < 1e-12

    # Risk-free independence of the conditioned betas is preserved.
    assert code_map["REL-04"].diagnostics["risk_free_required"] is False
    assert code_map["REL-05"].diagnostics["risk_free_required"] is False


def test_flat_benchmark_days_counted_and_excluded_from_conditioned_betas():
    # Benchmark alternates up / down / flat on a deterministic cycle.
    dates = _dates(760)
    bench_values = [1000.0]
    for i in range(len(dates) - 1):
        cycle = i % 3
        if cycle == 0:
            # Varying positive step: strictly positive with non-zero up-subsample variance.
            step = 0.0015 + 0.0006 * ((i // 3) % 5)
        elif cycle == 1:
            # Varying negative step: strictly negative with non-zero down-subsample variance.
            step = -(0.0012 + 0.0005 * ((i // 3) % 4))
        else:
            # Flat day: benchmark unchanged, return is exactly zero.
            step = 0.0
        bench_values.append(bench_values[-1] * (1.0 + step))
    nav_values = [100.0 * (1.0 + 0.001 * math.sin(i * 0.9)) for i in range(len(dates))]

    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, nav_values)
    ]
    bench_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, bench_values)
    ]

    items = dispatch_calculation(_request(nav_series, bench_series))
    code_map = {i.metric_code: i for i in items}

    paired = code_map["REL-01"].diagnostics["paired_count"]
    up = code_map["REL-05"].diagnostics["upside_count"]
    down = code_map["REL-04"].diagnostics["downside_count"]

    # Every third benchmark return is exactly zero and must be excluded from both.
    assert paired == 759
    assert up + down < paired
    assert code_map["REL-04"].status == CalculationStatus.CALCULATED
    assert code_map["REL-05"].status == CalculationStatus.CALCULATED

    # Values must equal kernel results computed on the full paired series
    # (kernels apply the same strict inequalities internally).
    fund_returns = stats_mod.periodic_returns(nav_values)
    bench_returns = stats_mod.periodic_returns(bench_values)
    assert abs(code_map["REL-04"].numeric_value - dbeta_mod.downside_beta(fund_returns, bench_returns)) < 1e-12
    assert abs(code_map["REL-05"].numeric_value - ubeta_mod.upside_beta(fund_returns, bench_returns)) < 1e-12


def test_insufficient_data_never_fabricates_zero_values():
    _, nav_series, bench_series = _build_series(740, bench_mode="increasing")

    items = dispatch_calculation(_request(nav_series, bench_series, metric_codes=["REL-04"]))
    item = items[0]
    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
    assert item.error_message
    # Anti-fabrication contract: NULL is reported, never 0.0.
    assert item.numeric_value != 0.0


def test_standard_beta_pairs_on_date_intersection_not_positional_tail():
    """Regression guard for the Beta discrepancy audit (REL-01 / MKT-01).

    REL-01 must pair fund and benchmark observations by ``effective_date``.
    The dispatcher may receive a benchmark series that carries dates the fund
    series does not (benchmark-only sessions). Pairing positionally, by slicing
    the tail of the longer benchmark return series, offsets the benchmark by
    one session per unmatched date and silently destroys the measurement.

    With the four benchmark-only sessions built below, the positional
    tail-slice shifts the benchmark four sessions and collapses Beta from
    ~0.92 to ~0.23: an index-tracking fund reported as barely correlated.
    """
    all_dates = _dates(760)
    bench_only_indices = (117, 233, 349, 465)
    fund_dates = [d for i, d in enumerate(all_dates) if i not in bench_only_indices]

    # Benchmark exists on every date, including the four the fund lacks. The
    # return path is deliberately near-uncorrelated across a four-session
    # shift, mirroring a real index series, so a positional misalignment
    # destroys the measurement instead of merely perturbing it.
    bench_steps = [
        0.004 * math.sin(i * 2.3999632) + 0.0025 * math.cos(i * 3.7173)
        for i in range(len(all_dates) - 1)
    ]
    bench_values = [1000.0]
    for step in bench_steps:
        bench_values.append(bench_values[-1] * (1.0 + step))
    bench_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(all_dates, bench_values)
    ]

    # Fund exists only on the intersection, and tracks the benchmark there.
    bench_map = dict(zip(all_dates, bench_values))
    intersection_bench_values = [bench_map[d] for d in fund_dates]
    intersection_bench_returns = stats_mod.periodic_returns(intersection_bench_values)
    fund_returns = [
        0.92 * b + 0.0015 * math.sin(i * 0.53) for i, b in enumerate(intersection_bench_returns)
    ]
    nav_values = [100.0]
    for r in fund_returns:
        nav_values.append(nav_values[-1] * (1.0 + r))
    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(fund_dates, nav_values)
    ]

    # Precondition: the benchmark really is four sessions longer than the fund.
    assert len(bench_series) == len(nav_series) + 4

    items = dispatch_calculation(_request(nav_series, bench_series, metric_codes=["REL-01"]))
    rel01 = items[0]
    assert rel01.status == CalculationStatus.CALCULATED

    # Paired count is the date-intersection return count, not either raw series.
    paired = len(intersection_bench_returns)
    assert paired == len(nav_series) - 1
    assert rel01.diagnostics["paired_count"] == paired

    # Canonical value: both sides computed on the strict date intersection,
    # with the risk-free series aligned to the same intersection dates.
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date.fromisoformat(o.effective_date),
            quoted_yield=0.0650,
            availability_time=datetime.datetime.fromisoformat(o.availability_time),
            revision_seq=o.revision_seq,
        )
        for o in nav_series
    ]
    aligned_rf = align_risk_free_series(
        observation_dates=fund_dates,
        risk_free_observations=rf_obs,
        analysis_cutoff=fund_dates[-1],
        knowledge_cutoff=f"{fund_dates[-1]}T23:59:59+05:30",
        max_lookback_days=4,
    )
    expected = beta_mod.beta(fund_returns, intersection_bench_returns, risk_free_rates=aligned_rf)
    assert abs(rel01.numeric_value - expected) < 1e-12
    assert expected > 0.8

    # Forbidden failure mode: positional tail-slice of the longer benchmark
    # return series paired against the fund return series.
    full_bench_returns = stats_mod.periodic_returns(bench_values)
    assert len(full_bench_returns) == paired + 4
    misaligned = beta_mod.beta(fund_returns, full_bench_returns[-paired:], risk_free_rates=aligned_rf)
    assert abs(misaligned - rel01.numeric_value) > 0.5
    assert misaligned < 0.4
