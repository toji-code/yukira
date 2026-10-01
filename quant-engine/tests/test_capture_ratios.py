import pytest
from src.capture import (
    downside_capture,
    upside_capture,
    capture_spread,
    compute_capture_metrics,
)
from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem


def test_capture_spread_calculation():
    assert capture_spread(105.0, 95.0) == pytest.approx(10.0)
    assert capture_spread(90.0, 95.0) == pytest.approx(-5.0)
    with pytest.raises(ValueError):
        capture_spread(float("nan"), 95.0)


def test_compute_capture_metrics_sufficient():
    # 150 up days, 100 down days
    p_ret = [0.01] * 150 + [-0.01] * 100
    b_ret = [0.008] * 150 + [-0.012] * 100

    metrics = compute_capture_metrics(p_ret, b_ret, min_up_days=150, min_down_days=100)

    assert metrics["total_paired_days"] == 250
    assert metrics["up_days_count"] == 150
    assert metrics["down_days_count"] == 100
    assert metrics["flat_days_count"] == 0
    assert metrics["is_up_sufficient"] is True
    assert metrics["is_down_sufficient"] is True
    assert metrics["upside_status"] == "CALCULATED"
    assert metrics["downside_status"] == "CALCULATED"
    assert metrics["spread_status"] == "CALCULATED"
    assert metrics["upside_capture"] > 100.0  # Fund gained more than benchmark on up days
    assert metrics["downside_capture"] < 100.0  # Fund lost less than benchmark on down days
    assert metrics["capture_spread"] == pytest.approx(metrics["upside_capture"] - metrics["downside_capture"])
    assert metrics["inverse_capture_gain"] is False


def test_compute_capture_metrics_insufficient():
    # Only 50 up days and 40 down days
    p_ret = [0.01] * 50 + [-0.01] * 40
    b_ret = [0.008] * 50 + [-0.012] * 40

    metrics = compute_capture_metrics(p_ret, b_ret, min_up_days=150, min_down_days=100)

    assert metrics["total_paired_days"] == 90
    assert metrics["up_days_count"] == 50
    assert metrics["down_days_count"] == 40
    assert metrics["is_up_sufficient"] is False
    assert metrics["is_down_sufficient"] is False
    assert metrics["upside_status"] == "INSUFFICIENT_DATA"
    assert metrics["downside_status"] == "INSUFFICIENT_DATA"
    assert metrics["spread_status"] == "INSUFFICIENT_DATA"
    assert metrics["upside_capture"] is None
    assert metrics["downside_capture"] is None
    assert metrics["capture_spread"] is None


def test_compute_capture_metrics_flat_days_and_ties():
    # Series with flat benchmark days
    p_ret = [0.02, -0.01, 0.005, -0.005, 0.03]
    b_ret = [0.01, -0.01, 0.0, 0.0, 0.02]

    metrics = compute_capture_metrics(p_ret, b_ret, min_up_days=2, min_down_days=1)

    assert metrics["total_paired_days"] == 5
    assert metrics["up_days_count"] == 2
    assert metrics["down_days_count"] == 1
    assert metrics["flat_days_count"] == 2
    assert metrics["upside_status"] == "CALCULATED"
    assert metrics["downside_status"] == "CALCULATED"


def test_compute_capture_metrics_inverse_gain():
    # Fund gains when benchmark declines
    p_ret = [0.01, 0.01]
    b_ret = [-0.02, -0.02]

    metrics = compute_capture_metrics(p_ret, b_ret, min_up_days=1, min_down_days=2)

    assert metrics["down_days_count"] == 2
    assert metrics["downside_status"] == "CALCULATED"
    assert metrics["downside_capture"] < 0.0  # Negative downside capture
    assert metrics["inverse_capture_gain"] is True


def test_dispatcher_mkt_metrics():
    # Create request with synthetic daily series
    dates = [f"2023-01-{i:02d}" for i in range(1, 21)]
    # Nav series (20 points)
    nav_series = [
        ObservationItem(effective_date=d, value=100.0 + i, availability_time=f"{d}T18:00:00+05:30")
        for i, d in enumerate(dates)
    ]
    # Bench series (20 points)
    bench_series = [
        ObservationItem(effective_date=d, value=1000.0 + i * 5, availability_time=f"{d}T18:00:00+05:30")
        for i, d in enumerate(dates)
    ]

    req = CalculationRequest(
        request_id="test-req-1",
        scheme_id="1",
        as_of_date="2023-01-20",
        knowledge_cutoff_time="2023-01-20T23:59:59+05:30",
        metric_codes=["MKT-03", "MKT-04", "MKT-05"],
        nav_series=nav_series,
        benchmark_series=bench_series,
        parameters={"min_upside_observations": 5, "min_downside_observations": 5},
    )

    items = dispatch_calculation(req)
    assert len(items) == 3
    code_map = {item.metric_code: item for item in items}

    # All bench returns are positive, so down days = 0 < 5 -> MKT-04 should be INSUFFICIENT_DATA
    assert code_map["MKT-03"].status == CalculationStatus.CALCULATED
    assert code_map["MKT-03"].numeric_value is not None
    assert code_map["MKT-04"].status == CalculationStatus.INSUFFICIENT_DATA
    assert code_map["MKT-05"].status == CalculationStatus.INSUFFICIENT_DATA
