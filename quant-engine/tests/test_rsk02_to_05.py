from __future__ import annotations

import datetime
import math
import pytest
from fastapi.testclient import TestClient

from src.api.dispatcher import dispatch_calculation
from src.api.main import app
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem
from src.risk import drawdown_details, maximum_drawdown, maximum_drawdown_duration
from src.semideviation import downside_semideviation
from src.ulcer_index import ulcer_index

client = TestClient(app)


# ============================================================================
# RSK-02 Downside Semideviation Unit Tests
# ============================================================================

def test_rsk02_all_positive_returns_zero_semideviation():
    returns = [0.01, 0.02, 0.015, 0.03]
    result = downside_semideviation(returns, target_return=0.0, periods_per_year=252.0)
    assert result == pytest.approx(0.0)


def test_rsk02_known_downside_returns():
    # returns: [-0.01, 0.02, -0.02, 0.01]
    # downside: [-0.01, 0.0, -0.02, 0.0]
    # sum of squares: 0.0001 + 0.0004 = 0.0005
    # divisor N-1 = 3 -> 0.0005 / 3 = 0.00016666666666666666
    # sqrt: 0.012909944487358056
    # annualized sqrt(252): 0.012909944487358056 * 15.874507866387544 = 0.2049390153193646
    returns = [-0.01, 0.02, -0.02, 0.01]
    result = downside_semideviation(returns, target_return=0.0, periods_per_year=252.0)
    expected = math.sqrt(0.0005 / 3.0) * math.sqrt(252.0)
    assert result == pytest.approx(expected, rel=1e-6)


def test_rsk02_requires_finite_inputs():
    with pytest.raises(ValueError):
        downside_semideviation([0.01, float("nan")])


# ============================================================================
# RSK-03 Maximum Drawdown Unit Tests
# ============================================================================

def test_rsk03_no_drawdown():
    values = [100.0, 105.0, 110.0, 120.0]
    assert maximum_drawdown(values) == pytest.approx(0.0)


def test_rsk03_known_drawdowns():
    values = [100.0, 120.0, 90.0, 110.0, 80.0, 130.0]
    # Peak 120 -> 90 (-25%), Peak 120 -> 80 (-33.3333%)
    details = drawdown_details(values, ["2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05", "2024-01-06"])
    assert details["max_drawdown"] == pytest.approx(-1.0 / 3.0, rel=1e-6)
    assert details["peak_nav"] == 120.0
    assert details["trough_nav"] == 80.0
    assert details["peak_date"] == "2024-01-02"
    assert details["trough_date"] == "2024-01-05"


# ============================================================================
# RSK-04 Maximum Drawdown Duration Unit Tests
# ============================================================================

def test_rsk04_no_drawdown_duration():
    values = [100.0, 105.0, 110.0]
    dates = ["2024-01-01", "2024-01-02", "2024-01-03"]
    result = maximum_drawdown_duration(values, dates)
    assert result["max_duration_calendar_days"] == 0
    assert result["max_duration_trading_days"] == 0
    assert not result["is_ongoing_at_cutoff"]


def test_rsk04_recovered_drawdown_duration():
    values = [100.0, 80.0, 90.0, 105.0]
    dates = ["2024-01-01", "2024-01-10", "2024-01-15", "2024-01-25"]
    result = maximum_drawdown_duration(values, dates)
    # Peak on 2024-01-01, recovered on 2024-01-25 (24 calendar days)
    assert result["max_duration_calendar_days"] == 24
    assert result["max_duration_trading_days"] == 3
    assert not result["is_ongoing_at_cutoff"]
    assert result["worst_episode_peak_date"] == "2024-01-01"
    assert result["worst_episode_recovery_date"] == "2024-01-25"
    assert result["worst_episode_trough_date"] == "2024-01-10"
    assert result["worst_episode_trough_nav"] == 80.0


def test_rsk04_ongoing_drawdown_duration():
    values = [100.0, 120.0, 100.0, 110.0]
    dates = ["2024-01-01", "2024-01-05", "2024-01-15", "2024-01-30"]
    result = maximum_drawdown_duration(values, dates)
    # Peak on 2024-01-05 (120.0), never recovered, cutoff 2024-01-30 -> 25 calendar days
    assert result["max_duration_calendar_days"] == 25
    assert result["max_duration_trading_days"] == 2
    assert result["is_ongoing_at_cutoff"] is True
    assert result["worst_episode_peak_date"] == "2024-01-05"
    assert result["worst_episode_recovery_date"] is None
    assert result["worst_episode_trough_date"] == "2024-01-15"
    assert result["worst_episode_trough_nav"] == 100.0


# ============================================================================
# RSK-05 Ulcer Index Unit Tests
# ============================================================================

def test_rsk05_ulcer_index_calculation():
    values = [100.0, 100.0, 90.0, 100.0]
    # Drawdowns: 0%, 0%, -10%, 0%
    # Squared drawdowns: 0, 0, 100, 0
    # Mean: 100 / 4 = 25
    # Sqrt: 5.0 UI points
    ui = ulcer_index(values)
    assert ui == pytest.approx(5.0, rel=1e-6)


# ============================================================================
# Dispatcher & REST API Tests for RSK-02 through RSK-05
# ============================================================================

def test_dispatcher_rsk02_to_05_execution():
    obs = [
        ObservationItem(effective_date="2021-01-01", value=100.0, availability_time="2021-01-01T23:59:59+05:30"),
        ObservationItem(effective_date="2021-06-01", value=120.0, availability_time="2021-06-01T23:59:59+05:30"),
        ObservationItem(effective_date="2022-01-01", value=90.0, availability_time="2022-01-01T23:59:59+05:30"),
        ObservationItem(effective_date="2023-01-01", value=110.0, availability_time="2023-01-01T23:59:59+05:30"),
        ObservationItem(effective_date="2024-01-01", value=130.0, availability_time="2024-01-01T23:59:59+05:30"),
    ]
    req = CalculationRequest(
        request_id="REQ-RSK-ALL-001",
        as_of_date="2024-01-01",
        knowledge_cutoff_time="2024-01-01T23:59:59+05:30",
        metric_codes=["RSK-02", "RSK-03", "RSK-04", "RSK-05"],
        nav_series=obs,
        parameters={"min_observations": 2, "periods_per_year": 252.0},
    )
    items = dispatch_calculation(req)
    assert len(items) == 4
    by_code = {it.metric_code: it for it in items}

    # RSK-02
    assert by_code["RSK-02"].status == CalculationStatus.CALCULATED
    assert by_code["RSK-02"].numeric_value is not None
    assert by_code["RSK-02"].numeric_value >= 0.0
    assert by_code["RSK-02"].units == "PERCENTAGE"
    assert by_code["RSK-02"].diagnostics["annualization_convention"] == "SQRT_252_CANDIDATE"

    # RSK-03
    assert by_code["RSK-03"].status == CalculationStatus.CALCULATED
    assert by_code["RSK-03"].numeric_value == pytest.approx(-0.25, rel=1e-4) # (90 - 120)/120 = -0.25
    assert by_code["RSK-03"].units == "PERCENTAGE"
    assert by_code["RSK-03"].diagnostics["peak_nav"] == 120.0
    assert by_code["RSK-03"].diagnostics["trough_nav"] == 90.0

    # RSK-04
    assert by_code["RSK-04"].status == CalculationStatus.CALCULATED
    assert by_code["RSK-04"].units == "DAYS"
    # Peak 2021-06-01 -> recovered 2024-01-01 = (2024-01-01 - 2021-06-01) = 944 days
    assert by_code["RSK-04"].numeric_value == 944.0
    assert by_code["RSK-04"].diagnostics["worst_episode_peak_date"] == "2021-06-01"
    assert by_code["RSK-04"].diagnostics["worst_episode_recovery_date"] == "2024-01-01"

    # RSK-05
    assert by_code["RSK-05"].status == CalculationStatus.CALCULATED
    assert by_code["RSK-05"].units == "POINTS"
    assert by_code["RSK-05"].numeric_value is not None
    assert by_code["RSK-05"].numeric_value > 0.0


def test_dispatcher_insufficient_data_threshold():
    obs = [
        ObservationItem(effective_date="2021-01-01", value=100.0, availability_time="2021-01-01T23:59:59+05:30"),
        ObservationItem(effective_date="2021-06-01", value=120.0, availability_time="2021-06-01T23:59:59+05:30"),
    ]
    req = CalculationRequest(
        request_id="REQ-INSUFFICIENT-RSK",
        as_of_date="2024-01-01",
        knowledge_cutoff_time="2024-01-01T23:59:59+05:30",
        metric_codes=["RSK-02", "RSK-03", "RSK-04", "RSK-05"],
        nav_series=obs,
        parameters={"min_observations": 700},
    )
    items = dispatch_calculation(req)
    for it in items:
        assert it.status == CalculationStatus.INSUFFICIENT_DATA
        assert it.numeric_value is None
