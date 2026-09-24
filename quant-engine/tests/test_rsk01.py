from __future__ import annotations

import math
import statistics as py_stats
import pytest
from fastapi.testclient import TestClient

from src.api.main import app
from src.api.models import CalculationRequest, ObservationItem, CalculationStatus
from src.api.dispatcher import dispatch_calculation
from src.statistics import periodic_returns, volatility

client = TestClient(app)


def test_rsk01_normal_valid_calculation():
    # 5 NAV points -> 4 returns
    navs = [100.0, 102.0, 101.0, 104.0, 106.0]
    returns = periodic_returns(navs)
    assert len(returns) == 4

    # Hand-derived sample std with N-1 divisor
    mean_r = sum(returns) / len(returns)
    variance_sample = sum((r - mean_r) ** 2 for r in returns) / (len(returns) - 1)
    std_sample = math.sqrt(variance_sample)
    expected_vol_252 = std_sample * math.sqrt(252.0)

    vol = volatility(returns, periods_per_year=252.0)
    assert vol == pytest.approx(expected_vol_252, rel=1e-9)
    assert vol == pytest.approx(py_stats.stdev(returns) * math.sqrt(252.0), rel=1e-9)


def test_rsk01_zero_variance():
    # Constant NAV -> returns are 0.0 -> variance is 0.0 -> volatility is 0.0
    navs = [100.0, 100.0, 100.0, 100.0]
    returns = periodic_returns(navs)
    vol = volatility(returns, periods_per_year=252.0)
    assert vol == 0.0


def test_rsk01_denominator_convention_distinction():
    # Explicitly test that N-1 (sample) is used rather than N (population)
    navs = [100.0, 110.0, 99.0]
    returns = periodic_returns(navs)  # [0.10, -0.10]
    # N = 2 returns
    # Sample std (N-1 = 1 divisor): sqrt(((0.10 - 0)^2 + (-0.10 - 0)^2) / 1) = sqrt(0.02) = 0.1414213562
    # Population std (N = 2 divisor): sqrt(((0.10 - 0)^2 + (-0.10 - 0)^2) / 2) = sqrt(0.01) = 0.10
    vol_sample = volatility(returns, periods_per_year=1.0)
    assert vol_sample == pytest.approx(math.sqrt(0.02), rel=1e-9)
    assert vol_sample != pytest.approx(0.10, rel=1e-3)


def test_rsk01_annualization_convention_parameterization():
    returns = [0.01, -0.005, 0.008, -0.002, 0.006]
    s = py_stats.stdev(returns)

    # Standard candidate 252 trading days
    vol_252 = volatility(returns, periods_per_year=252.0)
    assert vol_252 == pytest.approx(s * math.sqrt(252.0), rel=1e-9)

    # Candidate alternative 248 Indian exchange trading days
    vol_248 = volatility(returns, periods_per_year=248.0)
    assert vol_248 == pytest.approx(s * math.sqrt(248.0), rel=1e-9)
    assert vol_252 > vol_248


def test_rsk01_insufficient_observations_dispatcher():
    # When min_observations is 700 and 10 observations provided
    nav_series = [
        ObservationItem(
            effective_date=f"2024-01-{i:02d}",
            value=100.0 + i,
            availability_time="2024-01-15T23:59:59+05:30",
            revision_seq=1,
        )
        for i in range(1, 11)
    ]
    req = CalculationRequest(
        request_id="REQ-INSUFF-700",
        as_of_date="2024-01-10",
        knowledge_cutoff_time="2024-01-15T23:59:59+05:30",
        metric_codes=["RSK-01"],
        nav_series=nav_series,
        parameters={"min_observations": 700},
    )
    results = dispatch_calculation(req)
    assert len(results) == 1
    assert results[0].status == CalculationStatus.INSUFFICIENT_DATA
    assert results[0].numeric_value is None
    assert results[0].diagnostics["observation_count"] == 10
    assert results[0].diagnostics["min_observations_required"] == 700


def test_rsk01_invalid_non_positive_nav():
    # Non-positive NAV should raise ValueError in periodic_returns
    with pytest.raises(ValueError, match="greater than zero"):
        periodic_returns([100.0, 0.0, 105.0])

    with pytest.raises(ValueError, match="greater than zero"):
        periodic_returns([100.0, -10.0, 105.0])


def test_rsk01_non_finite_return_handling():
    with pytest.raises(ValueError, match="finite numbers"):
        periodic_returns([100.0, float("nan"), 105.0])

    with pytest.raises(ValueError, match="finite numbers"):
        periodic_returns([100.0, float("inf"), 105.0])


def test_rsk01_deterministic_repeatability():
    navs = [100.0 + (i * 0.35 * (-1 if i % 2 == 0 else 1)) for i in range(100)]
    returns = periodic_returns(navs)
    v1 = volatility(returns, periods_per_year=252.0)
    v2 = volatility(returns, periods_per_year=252.0)
    v3 = volatility(returns, periods_per_year=252.0)
    assert v1 == v2 == v3


def test_rsk01_api_endpoint_contract():
    payload = {
        "request_id": "REQ-RSK01-CONTRACT",
        "as_of_date": "2024-01-15",
        "knowledge_cutoff_time": "2024-01-15T23:59:59+05:30",
        "metric_codes": ["RSK-01"],
        "nav_series": [
            {"effective_date": "2024-01-01", "value": 100.0, "availability_time": "2024-01-01T23:59:59+05:30"},
            {"effective_date": "2024-01-02", "value": 101.5, "availability_time": "2024-01-02T23:59:59+05:30"},
            {"effective_date": "2024-01-03", "value": 100.8, "availability_time": "2024-01-03T23:59:59+05:30"},
            {"effective_date": "2024-01-04", "value": 102.1, "availability_time": "2024-01-04T23:59:59+05:30"},
        ],
        "parameters": {
            "periods_per_year": 252.0,
            "min_observations": 4,
        },
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "SUCCESS"
    assert len(data["results"]) == 1
    res = data["results"][0]
    assert res["metric_code"] == "RSK-01"
    assert res["status"] == "CALCULATED"
    assert res["units"] == "PERCENTAGE"
    assert res["numeric_value"] > 0.0
    assert res["diagnostics"]["methodology_status"] == "CANDIDATE"
    assert res["diagnostics"]["annualization_convention"] == "SQRT_252_CANDIDATE"
    assert res["diagnostics"]["denominator_convention"] == "N_MINUS_ONE_CANDIDATE"
