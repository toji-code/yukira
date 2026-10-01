"""
YUKIRA Tracking-Consistency dispatcher tests: REL-02 (Tracking Error) and
RAT-04 (Information Ratio).

Registry identity: these codes were previously exercised under MKT-01 / MKT-02,
which the frozen Phase 2H registry defines as Beta 3Y and Downside Beta. The
Phase 2R analytical profile (PHASE_2S_SCOPE_LOCK.md) and Contradiction Audit
Record 4 place Tracking Error at REL-02, and the frozen registry places the
Information Ratio at RAT-04. The verified gating / zero-tracking-error behaviour
below is unchanged; only the authoritative code identities are restored.
"""

import math
import pytest
from src.tracking_error import tracking_error
from src.information_ratio import information_ratio
from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem


def test_tracking_error_and_information_ratio_deterministic_math():
    # Deterministic test series (N = 5)
    p_ret = [0.012, -0.008, 0.015, 0.003, -0.004]
    b_ret = [0.010, -0.010, 0.012, 0.001, -0.002]

    # Active returns:
    # e = [0.002, 0.002, 0.003, 0.002, -0.002]
    e = [p - b for p, b in zip(p_ret, b_ret)]
    mean_e = sum(e) / len(e)  # 0.007 / 5 = 0.0014
    var_e = sum((x - mean_e) ** 2 for x in e) / (len(e) - 1)
    s_e = math.sqrt(var_e)

    te_unannualized = tracking_error(p_ret, b_ret)
    assert te_unannualized == pytest.approx(s_e, rel=1e-9)

    te_annualized = tracking_error(p_ret, b_ret, periods_per_year=252)
    assert te_annualized == pytest.approx(s_e * math.sqrt(252), rel=1e-9)

    ir_annualized = information_ratio(p_ret, b_ret, periods_per_year=252)
    expected_ir = (mean_e / s_e) * math.sqrt(252)
    assert ir_annualized == pytest.approx(expected_ir, rel=1e-9)

    # Invariant: IR = (mean_e * 252) / (s_e * sqrt(252))
    assert ir_annualized == pytest.approx((mean_e * 252) / te_annualized, rel=1e-9)


def test_dispatcher_rel02_rat04_normal_calculation():
    import datetime
    # 705 paired observations (exceeds N >= 700 threshold)
    nav_series = []
    bench_series = []
    p_val = 100.0
    b_val = 1000.0
    base_date = datetime.date(2021, 1, 1)

    for i in range(706):
        d_str = (base_date + datetime.timedelta(days=i)).isoformat()
        # Slight outperformance and variance
        if i > 0:
            p_ret = 0.0005 + (0.0002 if i % 2 == 0 else -0.0001)
            b_ret = 0.0004 + (0.0001 if i % 3 == 0 else -0.0001)
            p_val *= (1.0 + p_ret)
            b_val *= (1.0 + b_ret)

        nav_series.append(
            ObservationItem(
                effective_date=d_str,
                value=p_val,
                availability_time="2024-01-31T23:59:59+05:30",
                revision_seq=1,
            )
        )
        bench_series.append(
            ObservationItem(
                effective_date=d_str,
                value=b_val,
                availability_time="2024-01-31T23:59:59+05:30",
                revision_seq=1,
            )
        )

    req = CalculationRequest(
        request_id="REQ-REL02-RAT04-001",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
    )

    results = dispatch_calculation(req)
    res_map = {r.metric_code: r for r in results}

    assert "REL-02" in res_map
    assert "RAT-04" in res_map

    rel02 = res_map["REL-02"]
    assert rel02.status == CalculationStatus.CALCULATED
    assert rel02.units == "PERCENTAGE"
    assert rel02.period_type == "3Y"
    assert rel02.numeric_value is not None
    assert rel02.numeric_value > 0.0
    assert rel02.diagnostics["paired_count"] == 705
    assert rel02.diagnostics["min_paired_observations"] == 700
    assert rel02.diagnostics["annualization_convention"] == "SQRT_252"
    assert rel02.diagnostics["denominator_convention"] == "SAMPLE_VARIANCE_N_MINUS_1"

    rat04 = res_map["RAT-04"]
    assert rat04.status == CalculationStatus.CALCULATED
    assert rat04.units == "RATIO"
    assert rat04.period_type == "3Y"
    assert rat04.numeric_value is not None
    assert rat04.diagnostics["paired_count"] == 705
    assert rat04.diagnostics["min_paired_observations"] == 700
    assert rat04.diagnostics["annualization_convention"] == "SQRT_252_ON_DAILY_MEAN_OVER_TE"


def test_dispatcher_rel02_rat04_insufficient_observations():
    # Only 300 observations (< 700 threshold)
    nav_series = []
    bench_series = []
    p_val = 100.0
    b_val = 1000.0

    for i in range(301):
        d_str = f"2023-{(i // 28) % 12 + 1:02d}-{(i % 28) + 1:02d}"
        p_val *= 1.0005
        b_val *= 1.0004
        nav_series.append(ObservationItem(effective_date=d_str, value=p_val, availability_time="2024-01-31T23:59:59+05:30"))
        bench_series.append(ObservationItem(effective_date=d_str, value=b_val, availability_time="2024-01-31T23:59:59+05:30"))

    req = CalculationRequest(
        request_id="REQ-INSUFFICIENT-REL02",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
    )

    results = dispatch_calculation(req)
    res_map = {r.metric_code: r for r in results}

    assert res_map["REL-02"].status == CalculationStatus.INSUFFICIENT_DATA
    assert res_map["REL-02"].numeric_value is None
    assert "300 provided, minimum 700 required" in res_map["REL-02"].error_message
    assert res_map["REL-02"].diagnostics["paired_count"] == 300

    assert res_map["RAT-04"].status == CalculationStatus.INSUFFICIENT_DATA
    assert res_map["RAT-04"].numeric_value is None
    assert "300 provided, minimum 700 required" in res_map["RAT-04"].error_message
    assert res_map["RAT-04"].diagnostics["paired_count"] == 300


def test_dispatcher_zero_tracking_error_identical_series():
    # Identical series with N = 705 (exceeds threshold)
    import datetime
    nav_series = []
    bench_series = []
    val = 100.0
    base_date = datetime.date(2021, 1, 1)

    for i in range(706):
        d_str = (base_date + datetime.timedelta(days=i)).isoformat()
        if i > 0:
            ret = 0.001 if i % 2 == 0 else -0.0008
            val *= (1.0 + ret)
        nav_series.append(ObservationItem(effective_date=d_str, value=val, availability_time="2024-01-31T23:59:59+05:30"))
        # Benchmark identical level
        bench_series.append(ObservationItem(effective_date=d_str, value=val, availability_time="2024-01-31T23:59:59+05:30"))

    req = CalculationRequest(
        request_id="REQ-ZERO-TE",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
    )

    results = dispatch_calculation(req)
    res_map = {r.metric_code: r for r in results}

    # REL-02 Tracking Error is exactly 0.0
    assert res_map["REL-02"].status == CalculationStatus.CALCULATED
    assert res_map["REL-02"].numeric_value == pytest.approx(0.0, abs=1e-12)

    # RAT-04 Information Ratio cannot divide by zero; reports ERROR
    assert res_map["RAT-04"].status == CalculationStatus.ERROR
    assert res_map["RAT-04"].numeric_value is None
    assert "Zero tracking error" in res_map["RAT-04"].error_message
    assert res_map["RAT-04"].diagnostics["zero_tracking_error"] is True


def test_dispatcher_zero_tracking_error_constant_spread():
    # Active return is a constant c (variance is 0)
    import datetime
    nav_series = []
    bench_series = []
    p_val = 100.0
    b_val = 100.0
    base_date = datetime.date(2021, 1, 1)

    for i in range(706):
        d_str = (base_date + datetime.timedelta(days=i)).isoformat()
        b_ret = 0.001 if i % 2 == 0 else -0.0005
        # Fund return has constant 0.0002 extra every single day
        p_ret = b_ret + 0.0002
        if i > 0:
            p_val *= (1.0 + p_ret)
            b_val *= (1.0 + b_ret)
        nav_series.append(ObservationItem(effective_date=d_str, value=p_val, availability_time="2024-01-31T23:59:59+05:30"))
        bench_series.append(ObservationItem(effective_date=d_str, value=b_val, availability_time="2024-01-31T23:59:59+05:30"))

    req = CalculationRequest(
        request_id="REQ-CONST-SPREAD",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
    )

    results = dispatch_calculation(req)
    res_map = {r.metric_code: r for r in results}

    assert res_map["REL-02"].status == CalculationStatus.CALCULATED
    assert res_map["REL-02"].numeric_value == pytest.approx(0.0, abs=1e-12)
    assert res_map["RAT-04"].status == CalculationStatus.ERROR
    assert res_map["RAT-04"].diagnostics["zero_tracking_error"] is True


def test_dispatcher_missing_or_misaligned_benchmark():
    nav_series = [
        ObservationItem(effective_date="2024-01-01", value=100.0, availability_time="2024-01-31T23:59:59+05:30"),
        ObservationItem(effective_date="2024-01-02", value=101.0, availability_time="2024-01-31T23:59:59+05:30"),
        ObservationItem(effective_date="2024-01-03", value=102.0, availability_time="2024-01-31T23:59:59+05:30"),
    ]

    # No benchmark series provided
    req_no_bench = CalculationRequest(
        request_id="REQ-NO-BENCH",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=None,
    )
    results = dispatch_calculation(req_no_bench)
    for r in results:
        assert r.status == CalculationStatus.INSUFFICIENT_DATA
        assert "Aligned benchmark" in r.error_message

    # Completely misaligned dates
    misaligned_bench = [
        ObservationItem(effective_date="2020-01-01", value=1000.0, availability_time="2024-01-31T23:59:59+05:30"),
        ObservationItem(effective_date="2020-01-02", value=1005.0, availability_time="2024-01-31T23:59:59+05:30"),
    ]
    req_misaligned = CalculationRequest(
        request_id="REQ-MISALIGNED",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=misaligned_bench,
    )
    results_mis = dispatch_calculation(req_misaligned)
    for r in results_mis:
        assert r.status == CalculationStatus.INSUFFICIENT_DATA


def test_dispatcher_custom_min_paired_parameter():
    # If caller configures min_paired_observations = 5 for a custom test horizon:
    nav_series = []
    bench_series = []
    p_val, b_val = 100.0, 1000.0
    for i in range(11):
        d_str = f"2024-01-{i + 1:02d}"
        p_val *= (1.0 + (0.01 if i % 2 == 0 else -0.005))
        b_val *= (1.0 + (0.008 if i % 2 == 0 else -0.003))
        nav_series.append(ObservationItem(effective_date=d_str, value=p_val, availability_time="2024-01-31T23:59:59+05:30"))
        bench_series.append(ObservationItem(effective_date=d_str, value=b_val, availability_time="2024-01-31T23:59:59+05:30"))

    req = CalculationRequest(
        request_id="REQ-CUSTOM-PARAM",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=["REL-02", "RAT-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
        parameters={"min_paired_observations": 10},
    )

    results = dispatch_calculation(req)
    res_map = {r.metric_code: r for r in results}
    assert res_map["REL-02"].status == CalculationStatus.CALCULATED
    assert res_map["REL-02"].diagnostics["paired_count"] == 10
    assert res_map["RAT-04"].status == CalculationStatus.CALCULATED
    assert res_map["RAT-04"].diagnostics["paired_count"] == 10
