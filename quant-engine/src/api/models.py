from __future__ import annotations

from enum import Enum
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class DataCategory(str, Enum):
    OBSERVED_INPUT = "OBSERVED_INPUT"
    DERIVED_INPUT = "DERIVED_INPUT"
    CALCULATED_OUTPUT = "CALCULATED_OUTPUT"
    DIAGNOSTIC_INFORMATION = "DIAGNOSTIC_INFORMATION"


class CalculationStatus(str, Enum):
    CALCULATED = "CALCULATED"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"
    ERROR = "ERROR"
    SUPPRESSED = "SUPPRESSED"


class ObservationItem(BaseModel):
    """
    Represents an atomic historical financial observation.
    Category: OBSERVED_INPUT
    """
    effective_date: str = Field(..., description="Calendar date of measurement (YYYY-MM-DD)")
    value: float = Field(..., description="Observed numeric value (e.g. NAV or Index Level)")
    availability_time: str = Field(..., description="ISO-8601 publication/availability timestamp")
    revision_seq: int = Field(default=1, description="Monotonically increasing revision sequence")


class CalculationRequest(BaseModel):
    """
    Request envelope for deterministic quantitative execution.
    """
    request_id: str = Field(..., description="Unique calculation request identifier")
    scheme_id: Optional[str] = Field(None, description="Identifier of the mutual fund scheme option")
    benchmark_id: Optional[str] = Field(None, description="Identifier of the benchmark index")
    as_of_date: str = Field(..., description="As-of analysis date (YYYY-MM-DD)")
    knowledge_cutoff_time: str = Field(..., description="Strict information-set cutoff timestamp (ISO-8601)")
    methodology_version: str = Field(default="CANDIDATE-V1", description="Methodology version tag")
    metric_codes: List[str] = Field(..., description="List of candidate metric codes to compute")
    nav_series: List[ObservationItem] = Field(..., description="Historical NAV observation series")
    benchmark_series: Optional[List[ObservationItem]] = Field(default=None, description="Historical benchmark level series")
    risk_free_series: Optional[List[ObservationItem]] = Field(default=None, description="Historical risk-free observation series")
    parameters: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Execution parameters (e.g. risk_free_rate)")


class MetricOutputItem(BaseModel):
    """
    Individual calculated quantitative metric result.
    Category: CALCULATED_OUTPUT
    """
    metric_code: str = Field(..., description="Metric code (e.g. RET-01, RSK-01, RSK-03)")
    period_type: str = Field(default="1Y", description="Evaluation window (e.g. 1Y, 3Y, 5Y, LATEST)")
    numeric_value: Optional[float] = Field(None, description="Computed floating point numeric value")
    string_value: Optional[str] = Field(None, description="Optional string representation")
    units: str = Field(..., description="Unit of measurement (PERCENTAGE, RATIO, DAYS, etc.)")
    status: CalculationStatus = Field(default=CalculationStatus.CALCULATED, description="Calculation outcome status")
    diagnostics: Optional[Dict[str, Any]] = Field(default=None, description="Diagnostic and convention metadata")
    error_message: Optional[str] = Field(None, description="Failure reason if calculation failed")


class CalculationResponse(BaseModel):
    """
    Response envelope containing validated deterministic calculation results.
    """
    request_id: str
    as_of_date: str
    knowledge_cutoff_time: str
    engine_version: str
    git_commit_hash: str
    execution_duration_ms: float
    results: List[MetricOutputItem]
    status: str
    error_message: Optional[str] = None


class HealthResponse(BaseModel):
    """
    Health check payload.
    """
    status: str
    engine_version: str
    candidate_metrics: List[str]
    methodology_status: str
    empirical_findings: str
