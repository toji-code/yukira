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
        "metric_codes": ["RET-01", "RET-02", "RSK-01", "RSK-05"],
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
    assert len(data["results"]) == 4

    results_map = {r["metric_code"]: r for r in data["results"]}
    # Total return: (120 - 100) / 100 = 0.20
    assert pytest.approx(results_map["RET-02"]["numeric_value"], rel=1e-4) == 0.20
    assert results_map["RET-02"]["status"] == "CALCULATED"
    # Max drawdown: (105 - 110) / 110 = -0.04545
    assert results_map["RSK-05"]["numeric_value"] < 0
    # Volatility should be calculated and positive
    assert results_map["RSK-01"]["numeric_value"] > 0


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
    assert results_map["REL-01"]["status"] == "CALCULATED"
    assert results_map["REL-02"]["status"] == "CALCULATED"


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
