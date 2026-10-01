from __future__ import annotations

import pytest
from fastapi.testclient import TestClient
from src.api.main import app

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["engine_version"] == "0.1.0-alpha"
    assert "RET-01" in data["candidate_metrics"]
    assert "RAT-03" in data["candidate_metrics"]
    assert data["methodology_status"] == "STRICTLY EMPTY"
    assert data["empirical_findings"] == "EXACTLY ZERO"


def test_calculate_deterministic_returns():
    payload = {
        "request_id": "REQ-TEST-001",
        "as_of_date": "2026-01-05",
        "knowledge_cutoff_time": "2026-01-05T23:59:59+05:30",
        "metric_codes": ["RET-01", "RET-02", "RET-03", "RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05"],
        "nav_series": [
            {"effective_date": "2025-01-01", "value": 100.0, "availability_time": "2025-01-01T23:00:00+05:30"},
            {"effective_date": "2025-06-01", "value": 110.0, "availability_time": "2025-06-01T23:00:00+05:30"},
            {"effective_date": "2025-10-01", "value": 105.0, "availability_time": "2025-10-01T23:00:00+05:30"},
            {"effective_date": "2026-01-01", "value": 120.0, "availability_time": "2026-01-01T23:00:00+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["request_id"] == "REQ-TEST-001"
    assert data["status"] == "SUCCESS"
    assert len(data["results"]) == 8

    results_map = {r["metric_code"]: r for r in data["results"]}
    # Total return: (120 - 100) / 100 = 0.20
    assert pytest.approx(results_map["RET-02"]["numeric_value"], rel=1e-4) == 0.20
    assert results_map["RET-02"]["status"] == "CALCULATED"
    # Volatility should be calculated and positive
    assert results_map["RSK-01"]["numeric_value"] > 0
    # Downside semideviation should be calculated and >= 0
    assert results_map["RSK-02"]["numeric_value"] >= 0
    # Max drawdown: (105 - 110) / 110 = -0.04545
    assert results_map["RSK-03"]["numeric_value"] < 0
    # Max drawdown duration in days should be calculated and >= 0
    assert results_map["RSK-04"]["numeric_value"] >= 0
    # Ulcer index should be calculated and >= 0
    assert results_map["RSK-05"]["numeric_value"] >= 0
    # RET-03 should be calculated and positive
    assert results_map["RET-03"]["status"] == "CALCULATED"
    assert results_map["RET-03"]["numeric_value"] > 0


def test_calculate_candidate_sortino_conflict_metadata():
    payload = {
        "request_id": "REQ-SORTINO-001",
        "as_of_date": "2026-01-05",
        "knowledge_cutoff_time": "2026-01-05T23:59:59+05:30",
        "metric_codes": ["RAT-03"],
        "nav_series": [
            {"effective_date": "2025-01-01", "value": 100.0, "availability_time": "2025-01-01T23:00:00+05:30"},
            {"effective_date": "2025-02-01", "value": 98.0, "availability_time": "2025-02-01T23:00:00+05:30"},
            {"effective_date": "2025-03-01", "value": 102.0, "availability_time": "2025-03-01T23:00:00+05:30"},
            {"effective_date": "2025-04-01", "value": 105.0, "availability_time": "2025-04-01T23:00:00+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert len(data["results"]) == 1
    sortino_res = data["results"][0]
    assert sortino_res["metric_code"] == "RAT-03"
    assert sortino_res["status"] == "CALCULATED"
    assert sortino_res["diagnostics"]["convention"] == "downside_deviation_divisor_N"
    assert sortino_res["diagnostics"]["methodology_status"] == "CANDIDATE_UNRECONCILED_DIVISOR_CONFLICT"


def test_calculate_relative_metrics_with_benchmark():
    payload = {
        "request_id": "REQ-BETA-001",
        "as_of_date": "2026-01-05",
        "knowledge_cutoff_time": "2026-01-05T23:59:59+05:30",
        "metric_codes": ["REL-01", "REL-02"],
        "nav_series": [
            {"effective_date": "2025-01-01", "value": 100.0, "availability_time": "2025-01-01T23:00:00+05:30"},
            {"effective_date": "2025-01-02", "value": 101.0, "availability_time": "2025-01-02T23:00:00+05:30"},
            {"effective_date": "2025-01-03", "value": 102.0, "availability_time": "2025-01-03T23:00:00+05:30"},
        ],
        "benchmark_series": [
            {"effective_date": "2025-01-01", "value": 1000.0, "availability_time": "2025-01-01T23:00:00+05:30"},
            {"effective_date": "2025-01-02", "value": 1005.0, "availability_time": "2025-01-02T23:00:00+05:30"},
            {"effective_date": "2025-01-03", "value": 1015.0, "availability_time": "2025-01-03T23:00:00+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    results_map = {r["metric_code"]: r for r in data["results"]}
    # M2N-06 requires >= 700 synchronous paired trading days for REL-01.
    # 2 paired returns are deterministically insufficient (never a fabricated value).
    assert results_map["REL-01"]["status"] == "INSUFFICIENT_DATA"
    assert results_map["REL-01"]["numeric_value"] is None
    assert results_map["REL-01"]["diagnostics"]["paired_count"] == 2
    assert results_map["REL-01"]["diagnostics"]["min_paired_observations"] == 700
    # REL-02 is Tracking Error (Phase 2R / Phase 2S scope-lock registry identity).
    # Its frozen registry floor is 700 paired trading days, so 2 pairs are insufficient.
    assert results_map["REL-02"]["status"] == "INSUFFICIENT_DATA"
    assert results_map["REL-02"]["numeric_value"] is None
    assert results_map["REL-02"]["units"] == "PERCENTAGE"
    assert results_map["REL-02"]["diagnostics"]["paired_count"] == 2
    assert results_map["REL-02"]["diagnostics"]["min_paired_observations"] == 700


def test_calculate_insufficient_data():
    payload = {
        "request_id": "REQ-INSUFFICIENT-001",
        "as_of_date": "2026-01-05",
        "knowledge_cutoff_time": "2026-01-05T23:59:59+05:30",
        "metric_codes": ["RET-01"],
        "nav_series": [
            {"effective_date": "2025-01-01", "value": 100.0, "availability_time": "2025-01-01T23:00:00+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["results"][0]["status"] == "INSUFFICIENT_DATA"


def test_calculate_unsupported_metric_error():
    payload = {
        "request_id": "REQ-UNSUPPORTED-001",
        "as_of_date": "2026-01-05",
        "knowledge_cutoff_time": "2026-01-05T23:59:59+05:30",
        "metric_codes": ["INVALID-999"],
        "nav_series": [
            {"effective_date": "2025-01-01", "value": 100.0, "availability_time": "2025-01-01T23:00:00+05:30"},
            {"effective_date": "2025-01-02", "value": 101.0, "availability_time": "2025-01-02T23:00:00+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["results"][0]["status"] == "ERROR"


def test_calculate_with_risk_free_series():
    import datetime
    start_date = datetime.date(2025, 1, 1)
    nav_series = []
    benchmark_series = []
    risk_free_series = []
    # 720 observations -> 719 paired returns, satisfying the M2N-06 >= 700 floor for REL-01
    # and the >= 100 down-day floor for REL-04 on this monotonically declining series.
    for i in range(720):
        d_str = (start_date + datetime.timedelta(days=i)).isoformat()
        nav_series.append({"effective_date": d_str, "value": 100.0 - 0.05 * i, "availability_time": f"{d_str}T18:00:00+05:30"})
        benchmark_series.append({"effective_date": d_str, "value": 1000.0 - 0.5 * i, "availability_time": f"{d_str}T18:00:00+05:30"})
        risk_free_series.append({"effective_date": d_str, "value": 0.070, "availability_time": f"{d_str}T18:00:00+05:30"})

    as_of = (start_date + datetime.timedelta(days=719)).isoformat()
    payload = {
        "request_id": "REQ-RF-001",
        "as_of_date": as_of,
        "knowledge_cutoff_time": f"{as_of}T23:59:59+05:30",
        "metric_codes": ["RAT-01", "REL-01", "REL-04", "RAT-02"],
        "nav_series": nav_series,
        "benchmark_series": benchmark_series,
        "risk_free_series": risk_free_series,
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "SUCCESS"
    results_map = {r["metric_code"]: r for r in data["results"]}
    assert results_map["RAT-01"]["status"] == "CALCULATED"
    assert results_map["REL-01"]["status"] == "CALCULATED"
    assert results_map["REL-04"]["status"] == "CALCULATED"
    assert results_map["RAT-02"]["status"] == "CALCULATED"
    assert results_map["REL-01"]["diagnostics"]["risk_free_aligned"] is True
    assert results_map["REL-04"]["diagnostics"]["risk_free_required"] is False


def test_calculate_active_return_ret07():
    payload = {
        "request_id": "REQ-RET07-001",
        "as_of_date": "2024-01-15",
        "knowledge_cutoff_time": "2024-01-31T23:59:59+05:30",
        "metric_codes": ["RET-07", "REL-02", "REL-03"],
        "nav_series": [
            {"effective_date": "2021-01-15", "value": 100.0, "availability_time": "2024-01-31T23:59:59+05:30"},
            {"effective_date": "2024-01-15", "value": 200.0, "availability_time": "2024-01-31T23:59:59+05:30"},
        ],
        "benchmark_series": [
            {"effective_date": "2021-01-15", "value": 1000.0, "availability_time": "2024-01-31T23:59:59+05:30"},
            {"effective_date": "2024-01-15", "value": 1500.0, "availability_time": "2024-01-31T23:59:59+05:30"},
        ],
    }
    response = client.post("/api/v1/calculate", json=payload)
    assert response.status_code == 200
    data = response.json()
    results_map = {r["metric_code"]: r for r in data["results"]}
    assert results_map["RET-07"]["status"] == "CALCULATED"
    assert results_map["RET-07"]["period_type"] == "3Y"
    assert results_map["RET-07"]["numeric_value"] is not None
    # Fund CAGR: (200/100)^(1/3) - 1 approx 0.2599
    # Bench CAGR: (1500/1000)^(1/3) - 1 approx 0.1447
    # Active return = Fund CAGR - Bench CAGR
    assert results_map["RET-07"]["numeric_value"] > 0.05
    assert results_map["RET-07"]["diagnostics"]["methodology_status"] == "CANDIDATE"
