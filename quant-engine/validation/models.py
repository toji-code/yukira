"""Authoritative validation data models for Phase 2S-A.

NOTE ON GOVERNANCE BOUNDARY:
The outcomes in EvidenceOutcome represent empirical validation states only.
They MUST NOT be represented as governance states.
No metric automatically becomes VALIDATED or APPROVED because it receives PASS.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional


class EvidenceOutcome(str, Enum):
    """
    Five-state metric-level validation evidence outcome taxonomy.
    These are empirical verification outcomes, NOT governance states.
    """
    PASS = "PASS"
    CONDITIONAL = "CONDITIONAL"
    INSUFFICIENT = "INSUFFICIENT"
    BLOCKED = "BLOCKED"
    N_A = "N/A"


class ValidationTestType(str, Enum):
    """The 10 validation evidence modules established in Phase 2S."""
    METHODOLOGY_COMPLIANCE = "METHODOLOGY_COMPLIANCE"
    INDEPENDENT_PARITY = "INDEPENDENT_PARITY"
    PIT_PROVENANCE = "PIT_PROVENANCE"
    EDGE_CASE_ROBUSTNESS = "EDGE_CASE_ROBUSTNESS"
    SENSITIVITY_ANALYSIS = "SENSITIVITY_ANALYSIS"
    REGIME_STABILITY = "REGIME_STABILITY"
    STATISTICAL_UNCERTAINTY = "STATISTICAL_UNCERTAINTY"
    DEPENDENCY_ANALYSIS = "DEPENDENCY_ANALYSIS"
    DETERMINISTIC_REPRODUCIBILITY = "DETERMINISTIC_REPRODUCIBILITY"
    DOSSIER_COMPILATION = "DOSSIER_COMPILATION"


@dataclass
class ValidationRecord:
    """Detailed record of a single validation test execution."""
    metric_code: str
    test_type: ValidationTestType
    outcome: EvidenceOutcome
    production_value: Any = None
    reference_value: Any = None
    discrepancy: Optional[float] = None
    tolerance: Optional[float] = None
    tolerance_rule: str = ""
    methodology_version: str = "CANDIDATE_V1"
    analysis_cutoff: Optional[str] = None
    knowledge_cutoff: Optional[str] = None
    input_hash: Optional[str] = None
    diagnostics: dict[str, Any] = field(default_factory=dict)
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    def to_dict(self) -> dict[str, Any]:
        return {
            "metric_code": self.metric_code,
            "test_type": self.test_type.value,
            "outcome": self.outcome.value,
            "production_value": self.production_value,
            "reference_value": self.reference_value,
            "discrepancy": self.discrepancy,
            "tolerance": self.tolerance,
            "tolerance_rule": self.tolerance_rule,
            "methodology_version": self.methodology_version,
            "analysis_cutoff": self.analysis_cutoff,
            "knowledge_cutoff": self.knowledge_cutoff,
            "input_hash": self.input_hash,
            "diagnostics": self.diagnostics,
            "timestamp": self.timestamp,
        }


@dataclass
class MetricValidationSummary:
    """Summary of all validation records for a given metric."""
    metric_code: str
    overall_outcome: EvidenceOutcome
    records: list[ValidationRecord] = field(default_factory=list)
    epistemic_disclosures: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "metric_code": self.metric_code,
            "overall_outcome": self.overall_outcome.value,
            "records": [r.to_dict() for r in self.records],
            "epistemic_disclosures": self.epistemic_disclosures,
        }
