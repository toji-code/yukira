"""
YUKIRA Phase 2Q: Risk-Adjusted Metrics Production Integration Tests.

Verifies end-to-end analytical vertical slice across:
1. Sharpe Ratio 3Y (RAT-01) - M2N-01/M2N-02
2. Treynor Ratio 3Y (RAT-02 / RAT-05) - M2N-05/M2N-02/M2N-06
3. Beta 3Y (REL-01 / MKT-01) - M2N-06/M2N-02
4. Downside Beta 3Y (REL-04 / MKT-02) - M2N-07 (Strictly zero risk-free dependency)
"""

from __future__ import annotations

import datetime
import numpy as np
import pytest
from fastapi.testclient import TestClient

from src.api.main import app
from src.api.models import CalculationRequest, ObservationItem, CalculationStatus
from src.api.dispatcher import dispatch_calculation
from src.risk_free import RiskFreeObservation, align_risk_free_series
from src import beta as beta_mod, ratios as ratios_mod, treynor as treynor_mod, downside_beta as dbeta_mod

client = TestClient(app)


def test_sharpe_ratio_with_aligned_fbil_series():
    # 5 fund dates -> 4 return intervals
    # Effective dates: 2024-01-01 to 2024-01-05
    dates = ["2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
    navs = [100.0, 100.5, 99.8, 101.2, 102.0]
    rf_yields = [0.0695, 0.0698, 0.0700, 0.0692, 0.0694]

    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, navs)
    ]
    rf_series = [
        ObservationItem(effective_date=d, value=y, availability_time=f"{d}T18:00:00+05:30")
        for d, y in zip(dates, rf_yields)
    ]

    req = CalculationRequest(
        request_id="REQ-SHARPE-INT-01",
        as_of_date="2024-01-05",
        knowledge_cutoff_time="2024-01-05T23:59:59+05:30",
        metric_codes=["RAT-01"],
        nav_series=nav_series,
        risk_free_series=rf_series,
        parameters={"periods_per_year": 252.0},
    )

    results = dispatch_calculation(req)
    assert len(results) == 1
    r = results[0]
    assert r.metric_code == "RAT-01"
    assert r.status == CalculationStatus.CALCULATED
    assert r.period_type == "3Y"
    assert r.diagnostics["risk_free_aligned"] is True
    assert r.diagnostics["risk_free_proxy"] == "FBIL_91D_TBILL"
    assert r.diagnostics["annualization_convention"] == "SQRT_252_APPROVED"

    # Verify deterministic numerical precision
    p_returns = [(navs[i] - navs[i - 1]) / navs[i - 1] for i in range(1, len(navs))]
    rf_returns = [rf_yields[i - 1] * 1 / 365.0 for i in range(1, len(dates))]
    expected_sharpe = ratios_mod.sharpe_ratio(p_returns, rf_returns, periods_per_year=252.0)
    assert r.numeric_value == pytest.approx(expected_sharpe, abs=1e-12)


def test_treynor_and_beta_with_aligned_fbil_series():
    # 6 fund & benchmark dates -> 5 return intervals
    dates = ["2024-01-01", "2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05", "2024-01-08"]
    navs = [100.0, 100.5, 99.8, 101.2, 102.0, 103.5]
    bench = [1000.0, 1004.0, 995.0, 1010.0, 1018.0, 1030.0]
    rf_yields = [0.0695, 0.0698, 0.0700, 0.0692, 0.0694, 0.0696]

    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, navs)
    ]
    bench_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, bench)
    ]
    rf_series = [
        ObservationItem(effective_date=d, value=y, availability_time=f"{d}T18:00:00+05:30")
        for d, y in zip(dates, rf_yields)
    ]

    req = CalculationRequest(
        request_id="REQ-TREYNOR-BETA-01",
        as_of_date="2024-01-08",
        knowledge_cutoff_time="2024-01-08T23:59:59+05:30",
        metric_codes=["REL-01", "RAT-02"],
        nav_series=nav_series,
        benchmark_series=bench_series,
        risk_free_series=rf_series,
        parameters={"periods_per_year": 252.0},
    )

    results = dispatch_calculation(req)
    results_map = {res.metric_code: res for res in results}

    beta_res = results_map["REL-01"]
    treynor_res = results_map["RAT-02"]

    assert beta_res.status == CalculationStatus.CALCULATED
    assert beta_res.period_type == "3Y"
    assert beta_res.diagnostics["risk_free_aligned"] is True
    assert beta_res.diagnostics["risk_free_proxy"] == "FBIL_91D_TBILL"

    assert treynor_res.status == CalculationStatus.CALCULATED
    assert treynor_res.period_type == "3Y"
    assert treynor_res.diagnostics["risk_free_aligned"] is True
    assert treynor_res.diagnostics["annualization_convention"] == "252_MULTIPLIER_APPROVED"

    # Verify exact numerical equivalence
    p_returns = [(navs[i] - navs[i - 1]) / navs[i - 1] for i in range(1, len(navs))]
    b_returns = [(bench[i] - bench[i - 1]) / bench[i - 1] for i in range(1, len(bench))]
    # Weekend accrual on 2024-01-05 -> 2024-01-08 is 3 days
    delta_days = [1, 1, 1, 1, 3]
    rf_returns = [rf_yields[i] * delta_days[i] / 365.0 for i in range(5)]

    expected_beta = beta_mod.beta(p_returns, b_returns, risk_free_rates=rf_returns)
    expected_treynor = treynor_mod.treynor_ratio(p_returns, rf_returns, expected_beta, periods_per_year=252.0)

    assert beta_res.numeric_value == pytest.approx(expected_beta, abs=1e-12)
    assert treynor_res.numeric_value == pytest.approx(expected_treynor, abs=1e-12)


def test_downside_beta_proves_risk_free_independence():
    import datetime
    start_date = datetime.date(2024, 1, 1)
    dates = [(start_date + datetime.timedelta(days=i)).isoformat() for i in range(105)]
    navs = [100.0 - 0.05 * i for i in range(105)]
    bench = [1000.0 - 0.5 * i for i in range(105)]

    nav_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, navs)
    ]
    bench_series = [
        ObservationItem(effective_date=d, value=v, availability_time=f"{d}T18:00:00+05:30")
        for d, v in zip(dates, bench)
    ]

    as_of = dates[-1]
    # Run without risk_free_series
    req_no_rf = CalculationRequest(
        request_id="REQ-DBETA-NO-RF",
        as_of_date=as_of,
        knowledge_cutoff_time=f"{as_of}T23:59:59+05:30",
        metric_codes=["REL-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
    )
    res_no_rf = dispatch_calculation(req_no_rf)[0]

    # Run with arbitrary risk_free_series
    rf_series = [
        ObservationItem(effective_date=d, value=0.080, availability_time=f"{d}T18:00:00+05:30")
        for d in dates
    ]
    req_with_rf = CalculationRequest(
        request_id="REQ-DBETA-WITH-RF",
        as_of_date=as_of,
        knowledge_cutoff_time=f"{as_of}T23:59:59+05:30",
        metric_codes=["REL-04"],
        nav_series=nav_series,
        benchmark_series=bench_series,
        risk_free_series=rf_series,
    )
    res_with_rf = dispatch_calculation(req_with_rf)[0]

    # Downside Beta MUST be identical regardless of whether risk_free_series is present or not
    assert res_no_rf.numeric_value == pytest.approx(res_with_rf.numeric_value, abs=1e-15)
    assert res_with_rf.diagnostics["risk_free_required"] is False
    assert res_with_rf.diagnostics["annualization"] == "NONE"


def test_missing_risk_free_observation_fails_safely_without_silent_zero():
    # Provide NAV series spanning 10 days, but risk-free series has a gap > 4-day lookback
    nav_series = [
        ObservationItem(effective_date="2024-01-01", value=100.0, availability_time="2024-01-01T18:00:00+05:30"),
        ObservationItem(effective_date="2024-01-02", value=100.5, availability_time="2024-01-02T18:00:00+05:30"),
        ObservationItem(effective_date="2024-01-10", value=102.0, availability_time="2024-01-10T18:00:00+05:30"),
        ObservationItem(effective_date="2024-01-11", value=102.5, availability_time="2024-01-11T18:00:00+05:30"),
    ]
    # Only 2024-01-01 quote provided; interval from Jan 10 to Jan 11 has start_date Jan 10 (9 days gap > 4 days)
    rf_series = [
        ObservationItem(effective_date="2024-01-01", value=0.0695, availability_time="2024-01-01T18:00:00+05:30"),
    ]

    req = CalculationRequest(
        request_id="REQ-GAP-RF",
        as_of_date="2024-01-11",
        knowledge_cutoff_time="2024-01-11T23:59:59+05:30",
        metric_codes=["RAT-01"],
        nav_series=nav_series,
        risk_free_series=rf_series,
    )
    results = dispatch_calculation(req)
    r = results[0]
    assert r.status == CalculationStatus.INSUFFICIENT_DATA
    assert "Risk-free rate alignment failed" in r.error_message
    assert r.numeric_value is None


def test_pit_excludes_future_risk_free_observations():
    # Observations published on Jan 10 with availability timestamp Jan 10 18:00
    # Knowledge cutoff is Jan 05 23:59:59
    nav_series = [
        ObservationItem(effective_date="2024-01-01", value=100.0, availability_time="2024-01-01T18:00:00+05:30"),
        ObservationItem(effective_date="2024-01-02", value=100.5, availability_time="2024-01-02T18:00:00+05:30"),
        ObservationItem(effective_date="2024-01-05", value=101.0, availability_time="2024-01-05T18:00:00+05:30"),
    ]
    rf_series = [
        ObservationItem(effective_date="2024-01-01", value=0.0695, availability_time="2024-01-01T18:00:00+05:30"),
        # Future quote published after knowledge cutoff
        ObservationItem(effective_date="2024-01-04", value=0.0750, availability_time="2024-01-10T18:00:00+05:30"),
    ]

    req = CalculationRequest(
        request_id="REQ-PIT-EXCLUDE",
        as_of_date="2024-01-05",
        knowledge_cutoff_time="2024-01-05T23:59:59+05:30",
        metric_codes=["RAT-01"],
        nav_series=nav_series,
        risk_free_series=rf_series,
    )
    results = dispatch_calculation(req)
    # Jan 04 quote is excluded by knowledge cutoff -> Jan 01 quote (0.0695) is used via 4-day lookback
    r = results[0]
    assert r.status == CalculationStatus.CALCULATED



def test_full_fastapi_endpoint_risk_adjusted_suite():
    import datetime
    start_date = datetime.date(2024, 1, 1)
    nav_series = []
    benchmark_series = []
    risk_free_series = []
    for i in range(105):
        d_str = (start_date + datetime.timedelta(days=i)).isoformat()
        nav_series.append({"effective_date": d_str, "value": 100.0 - 0.05 * i, "availability_time": f"{d_str}T18:00:00+05:30"})
        benchmark_series.append({"effective_date": d_str, "value": 1000.0 - 0.5 * i, "availability_time": f"{d_str}T18:00:00+05:30"})
        risk_free_series.append({"effective_date": d_str, "value": 0.0695, "availability_time": f"{d_str}T18:00:00+05:30"})

    as_of = (start_date + datetime.timedelta(days=104)).isoformat()
    payload = {
        "request_id": "REQ-FASTAPI-FULL-SUITE",
        "as_of_date": as_of,
        "knowledge_cutoff_time": f"{as_of}T23:59:59+05:30",
        "metric_codes": ["RAT-01", "RAT-02", "REL-01", "REL-04"],
        "nav_series": nav_series,
        "benchmark_series": benchmark_series,
        "risk_free_series": risk_free_series,
    }

    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "SUCCESS"
    results = {r["metric_code"]: r for r in data["results"]}
    assert results["RAT-01"]["status"] == "CALCULATED"
    assert results["RAT-02"]["status"] == "CALCULATED"
    assert results["REL-01"]["status"] == "CALCULATED"
    assert results["REL-04"]["status"] == "CALCULATED"
