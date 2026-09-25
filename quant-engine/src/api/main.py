from __future__ import annotations

import time
import subprocess
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from src.api.models import (
    CalculationRequest,
    CalculationResponse,
    HealthResponse,
)
from src.api.dispatcher import dispatch_calculation

app = FastAPI(
    title="YUKIRA Quantitative Engine API",
    description="Deterministic quantitative calculation microservice for Project YUKIRA.",
    version="0.1.0-alpha",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def _get_git_commit() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], stderr=subprocess.DEVNULL
        ).decode("ascii").strip()
    except Exception:
        return "0000000000000000000000000000000000000000"


@app.get("/health", response_model=HealthResponse)
def health_check() -> HealthResponse:
    return HealthResponse(
        status="UP",
        engine_version="0.1.0-alpha",
        candidate_metrics=[
            "RET-01", "RET-02", "RET-03", "RET-07",
            "RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05", "RSK-06", "RSK-07",
            "REL-01", "REL-02", "REL-03", "REL-04",
            "RAT-01", "RAT-02", "RAT-03", "RAT-04",
        ],
        methodology_status="STRICTLY EMPTY",
        empirical_findings="EXACTLY ZERO",
    )


@app.post("/api/v1/calculate", response_model=CalculationResponse)
def calculate_metrics(request: CalculationRequest) -> CalculationResponse:
    start_time = time.perf_counter()

    try:
        results = dispatch_calculation(request)
        duration_ms = (time.perf_counter() - start_time) * 1000.0

        overall_status = "SUCCESS"
        if any(r.status == "ERROR" for r in results):
            overall_status = "PARTIAL_SUCCESS" if any(r.status == "CALCULATED" for r in results) else "ERROR"

        return CalculationResponse(
            request_id=request.request_id,
            as_of_date=request.as_of_date,
            knowledge_cutoff_time=request.knowledge_cutoff_time,
            engine_version="0.1.0-alpha",
            git_commit_hash=_get_git_commit(),
            execution_duration_ms=round(duration_ms, 3),
            results=results,
            status=overall_status,
        )
    except Exception as e:
        duration_ms = (time.perf_counter() - start_time) * 1000.0
        return CalculationResponse(
            request_id=request.request_id,
            as_of_date=request.as_of_date,
            knowledge_cutoff_time=request.knowledge_cutoff_time,
            engine_version="0.1.0-alpha",
            git_commit_hash=_get_git_commit(),
            execution_duration_ms=round(duration_ms, 3),
            results=[],
            status="ERROR",
            error_message=str(e),
        )
