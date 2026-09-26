"""Validation framework package for Project YUKIRA Phase 2S.

Authoritative quantitative validation engine covering the 10 evidence modules
for empirical verification of financial calculation metrics.
"""

from validation.models import (
    EvidenceOutcome,
    ValidationTestType,
    ValidationRecord,
    MetricValidationSummary,
)
from validation.tolerances import (
    get_tolerance_spec,
    assess_parity,
    REPRESENTATIVE_TOLERANCES,
)
from validation.reference_kernels import (
    ref_cagr,
    ref_volatility,
    ref_maximum_drawdown,
    ref_historical_var,
    ref_sharpe_ratio,
    ref_beta,
)
from validation.regime_engine import (
    FROZEN_REGIMES,
    get_frozen_regime,
    assert_regimes_frozen,
    RegimeFreezeError,
)
from validation.pit_verifier import (
    compute_provenance_digest,
    filter_by_pit_contract,
    assert_zero_future_leakage,
)
from validation.uncertainty import (
    evaluate_metric_uncertainty,
    estimate_dependence_block_length,
)
from validation.dependency import (
    get_descriptive_dependency,
    REPRESENTATIVE_DEPENDENCIES,
)
from validation.edge_cases import (
    run_all_edge_case_tests,
    EdgeCaseTestResult,
)
from validation.harness import Phase2SAValidationHarness
from validation.dossier import (
    generate_dossier_markdown,
    generate_dossier_json,
)

__all__ = [
    "EvidenceOutcome",
    "ValidationTestType",
    "ValidationRecord",
    "MetricValidationSummary",
    "get_tolerance_spec",
    "assess_parity",
    "REPRESENTATIVE_TOLERANCES",
    "ref_cagr",
    "ref_volatility",
    "ref_maximum_drawdown",
    "ref_historical_var",
    "ref_sharpe_ratio",
    "ref_beta",
    "FROZEN_REGIMES",
    "get_frozen_regime",
    "assert_regimes_frozen",
    "RegimeFreezeError",
    "compute_provenance_digest",
    "filter_by_pit_contract",
    "assert_zero_future_leakage",
    "evaluate_metric_uncertainty",
    "estimate_dependence_block_length",
    "get_descriptive_dependency",
    "REPRESENTATIVE_DEPENDENCIES",
    "run_all_edge_case_tests",
    "EdgeCaseTestResult",
    "Phase2SAValidationHarness",
    "generate_dossier_markdown",
    "generate_dossier_json",
]
