"""Automated test suite for Phase 2S-A Quantitative Validation Framework.

Tests cover all 10 validation evidence modules:
1. Methodology / specification compliance
2. Independent reference parity
3. Metric-specific tolerances (asserting no universal 1e-12)
4. Reference vector suite
5. Point-in-Time bitemporal isolation and future leakage detection
6. Regime freeze gate enforcement
7. Metric-specific statistical uncertainty (block bootstrap and path-dependent exclusion)
8. Edge-case robustness
9. Descriptive dependency analysis
10. Deterministic reproducibility and evidence dossier generation
"""

from __future__ import annotations

import math
import pytest
import numpy as np

from validation.models import EvidenceOutcome, ValidationTestType
from validation.tolerances import (
    get_tolerance_spec,
    assess_parity,
    REPRESENTATIVE_TOLERANCES,
)
from validation.reference_kernels import (
    ref_cagr,
    ref_simple_returns,
    ref_volatility,
    ref_volatility_from_nav,
    ref_maximum_drawdown,
    ref_historical_var,
    ref_sharpe_ratio,
    ref_beta,
)
from validation.reference_vectors import ALL_REFERENCE_VECTORS
from validation.pit_verifier import (
    compute_provenance_digest,
    filter_by_pit_contract,
    assert_zero_future_leakage,
)
from validation.regime_engine import (
    FROZEN_REGIMES,
    get_frozen_regime,
    assert_regimes_frozen,
    RegimeFreezeError,
    slice_series_by_regime,
)
from validation.uncertainty import (
    evaluate_metric_uncertainty,
    estimate_dependence_block_length,
)
from validation.dependency import get_descriptive_dependency
from validation.edge_cases import run_all_edge_case_tests
from validation.harness import Phase2SAValidationHarness
from validation.dossier import generate_dossier_markdown, generate_dossier_json

# Production kernels
from src.returns import cagr_from_calendar_days
from src.statistics import volatility as prod_volatility
from src.risk import maximum_drawdown as prod_maximum_drawdown
from src.var import historical_var as prod_historical_var
from src.ratios import sharpe_ratio as prod_sharpe_ratio
from src.beta import beta as prod_beta


# ============================================================================
# 1. Independent Reference Kernels Mathematical Correctness Tests
# ============================================================================

def test_ref_cagr_mathematical_precision():
    # 1000 -> 1331 in 3 years = exactly 10% per year under 365.25 day count
    # days = 3 * 365.25 = 1095.75
    val = ref_cagr(1000.0, 1331.0, 1095.75)
    assert abs(val - 0.10) < 1e-12

    # Negative growth: 1000 -> 729 in 3 years = -10% per year
    val_neg = ref_cagr(1000.0, 729.0, 1095.75)
    assert abs(val_neg - (-0.10)) < 1e-12


def test_ref_volatility_mathematical_precision():
    # returns: [0.01, -0.01, 0.01, -0.01] -> mean = 0.0
    # var = (0.0001 * 4) / 3 = 0.0004 / 3
    # std = sqrt(0.0004 / 3) = 0.01154700538
    # annualized sqrt(252) = 0.183303027798
    returns = [0.01, -0.01, 0.01, -0.01]
    vol = ref_volatility(returns, periods_per_year=252.0)
    expected = math.sqrt(0.0004 / 3.0) * math.sqrt(252.0)
    assert abs(vol - expected) < 1e-12


def test_ref_rsk01_simple_returns_parity():
    """Verify that RSK-01 reference uses discrete simple returns per Phase 2H Line 431."""
    navs = [100.0, 105.0, 102.0, 108.0]
    simple_rets = ref_simple_returns(navs)
    # Simple returns: [5/100, -3/105, 6/102] = [0.05, -0.02857142857, 0.05882352941]
    assert abs(simple_rets[0] - 0.05) < 1e-12
    assert abs(simple_rets[1] - (-3.0 / 105.0)) < 1e-12

    # Volatility computed from NAV matches volatility computed from simple returns
    vol_nav = ref_volatility_from_nav(navs)
    vol_rets = ref_volatility(simple_rets)
    assert abs(vol_nav - vol_rets) < 1e-15

    # Logarithmic returns ln(NAV_t / NAV_{t-1}) differ materially and are rejected
    log_rets = [math.log(curr / prev) for prev, curr in zip(navs[:-1], navs[1:])]
    vol_log = ref_volatility(log_rets)
    assert abs(vol_nav - vol_log) > 1e-4


def test_ref_maximum_drawdown_precision():
    navs = [100.0, 120.0, 110.0, 90.0, 95.0, 130.0]
    # Peak at 120, trough at 90 -> (90 - 120) / 120 = -30 / 120 = -0.25 (-25%)
    mdd = ref_maximum_drawdown(navs)
    assert mdd == -0.25


def test_ref_historical_var_precision():
    # 21 points: -0.10 to +0.10 in steps of 0.01
    # 5th percentile is at index 1 -> value is -0.09 -> VaR = +0.09
    returns = [round(-0.10 + i * 0.01, 4) for i in range(21)]
    var_95 = ref_historical_var(returns, confidence_level=0.95)
    assert abs(var_95 - 0.09) < 1e-10


def test_ref_sharpe_ratio_precision():
    # returns: [0.02, -0.01, 0.03, 0.00, 0.01], rf = 0.0002
    returns = [0.02, -0.01, 0.03, 0.00, 0.01]
    rf = 0.0002
    sr = ref_sharpe_ratio(returns, rf, periods_per_year=252.0)
    excess = [r - rf for r in returns]
    expected = (np.mean(excess) / np.std(excess, ddof=1)) * math.sqrt(252.0)
    assert abs(sr - expected) < 1e-12


def test_ref_beta_precision():
    # Portfolio is exactly 1.5x benchmark
    bench = [-0.02, -0.01, 0.00, 0.01, 0.02]
    port = [b * 1.5 for b in bench]
    b_val = ref_beta(port, bench)
    assert abs(b_val - 1.5) < 1e-12


# ============================================================================
# 2. Production vs Independent Reference Parity & Metric-Specific Tolerances
# ============================================================================

def test_metric_specific_tolerances_not_universal_1e12():
    """Verify that tolerances are pre-specified, metric-specific, and NOT universally 1e-12."""
    tol_ret03 = get_tolerance_spec("RET-03").absolute_tolerance
    tol_rsk01 = get_tolerance_spec("RSK-01").absolute_tolerance
    tol_rsk06 = get_tolerance_spec("RSK-06").absolute_tolerance
    tol_rat01 = get_tolerance_spec("RAT-01").absolute_tolerance
    tol_rel01 = get_tolerance_spec("REL-01").absolute_tolerance

    # Assert distinct pre-specified values reflecting estimator properties
    assert tol_ret03 == 1e-10
    assert tol_rsk01 == 1e-12
    assert tol_rsk06 == 1e-12  # Exact continuous linear quantile convention parity verified
    assert tol_rat01 == 1e-8
    assert tol_rel01 == 1e-8

    # Assert that all 6 representative metrics have explicit justifications
    for code in ("RET-03", "RSK-01", "RSK-03", "RSK-06", "RAT-01", "REL-01"):
        spec = get_tolerance_spec(code)
        assert len(spec.justification) > 20


def test_parity_assessment_pass_and_failure_detection():
    # Pass scenario
    passed, diff, rule = assess_parity("RSK-01", 0.13476625, 0.1347662500000001)
    assert passed is True
    assert diff < 1e-12

    # Injected discrepancy scenario: must detect failure
    passed_fail, diff_fail, rule_fail = assess_parity("RSK-01", 0.13476625, 0.14476625)
    assert passed_fail is False
    assert diff_fail > 0.001
    assert "Failed pre-specified tolerance" in rule_fail


# ============================================================================
# 3. Reference Vector Suite Verification
# ============================================================================

def test_reference_vectors_coverage_all_representative_metrics():
    """Assert that every representative metric defines all four required vector scenarios."""
    for code in ("RET-03", "RSK-01", "RSK-03", "RSK-06", "RAT-01", "REL-01"):
        suite = ALL_REFERENCE_VECTORS[code]
        assert suite.normal_case is not None
        assert suite.boundary_case is not None
        assert suite.insufficient_case is not None
        assert suite.degenerate_case is not None


# ============================================================================
# 4. Point-in-Time (PIT) & Future Leakage Tests
# ============================================================================

def test_pit_bitemporal_filtering():
    sample_obs = [
        {"date": "2023-12-31", "value": 100.0, "availability_time": "2024-01-01T18:00:00+00:00"},
        {"date": "2024-01-15", "value": 105.0, "availability_time": "2024-01-15T18:00:00+00:00"},
        # Future effective date (after analysis cutoff 2024-01-15)
        {"date": "2024-01-16", "value": 106.0, "availability_time": "2024-01-16T18:00:00+00:00"},
        # Future availability time (effective 2024-01-10, but published after knowledge cutoff 2024-01-31)
        {"date": "2024-01-10", "value": 104.0, "availability_time": "2024-02-05T18:00:00+00:00"},
    ]

    filtered = filter_by_pit_contract(
        sample_obs,
        analysis_cutoff="2024-01-15",
        knowledge_cutoff="2024-01-31T23:59:59+00:00",
    )

    # Exactly 2 observations must survive
    assert len(filtered) == 2
    dates = [x["date"] for x in filtered]
    assert "2023-12-31" in dates
    assert "2024-01-15" in dates
    assert "2024-01-16" not in dates
    assert "2024-01-10" not in dates  # Excluded due to late availability time!

    is_clean, violations = assert_zero_future_leakage(
        filtered,
        analysis_cutoff="2024-01-15",
        knowledge_cutoff="2024-01-31T23:59:59+00:00",
    )
    assert is_clean is True
    assert len(violations) == 0


def test_pit_future_leakage_detection():
    # Injected dirty dataset with future observation
    dirty_obs = [
        {"date": "2024-01-15", "value": 100.0, "availability_time": "2024-01-15T18:00:00+00:00"},
        {"date": "2024-02-01", "value": 105.0, "availability_time": "2024-02-01T18:00:00+00:00"},
    ]
    is_clean, violations = assert_zero_future_leakage(
        dirty_obs,
        analysis_cutoff="2024-01-15",
        knowledge_cutoff="2024-01-31T23:59:59+00:00",
    )
    assert is_clean is False
    assert len(violations) >= 1


# ============================================================================
# 5. Regime Freeze Gate Tests
# ============================================================================

def test_regime_freeze_gate_permanent_lock():
    assert_regimes_frozen()
    regimes = list(FROZEN_REGIMES.keys())
    assert set(regimes) == {"REG-01", "REG-02", "REG-03"}

    # Assert exact frozen date boundaries from locked Phase 2S scope
    reg1 = get_frozen_regime("REG-01")
    assert reg1.start_date == "2020-02-01"
    assert reg1.end_date == "2020-11-30"

    reg2 = get_frozen_regime("REG-02")
    assert reg2.start_date == "2020-12-01"
    assert reg2.end_date == "2021-12-31"

    reg3 = get_frozen_regime("REG-03")
    assert reg3.start_date == "2022-04-01"
    assert reg3.end_date == "2023-04-30"


# ============================================================================
# 6. Metric-Specific Uncertainty & Path-Dependency Protection Tests
# ============================================================================

def test_uncertainty_path_dependent_rsk03_strictly_inapplicable():
    """Verify that path-dependent RSK-03 is strictly protected from block bootstrap."""
    res = evaluate_metric_uncertainty("RSK-03", fund_returns=[0.01] * 100)
    assert res.outcome == EvidenceOutcome.N_A
    assert res.methodology == "INAPPLICABLE_FOR_PATH_DEPENDENT"
    assert any("Path-dependent metric" in d for d in res.disclosures)
    assert any("Specialized path-preserving uncertainty methods are outside the current 2S-A scope" in d for d in res.disclosures)


def test_uncertainty_discrete_cagr_ret03_inapplicable():
    """Verify that discrete point-to-point RET-03 is protected from return permutation."""
    res = evaluate_metric_uncertainty("RET-03", fund_returns=[0.01] * 100)
    assert res.outcome == EvidenceOutcome.N_A
    assert res.methodology == "INAPPLICABLE_FOR_DISCRETE_ENDPOINT"
    assert any("A separate uncertainty methodology would require explicit methodological justification and is outside the current 2S-A scope" in d for d in res.disclosures)


def test_uncertainty_block_bootstrap_rsk01_deterministic():
    rng = np.random.default_rng(123)
    synthetic_returns = rng.normal(0.0005, 0.01, size=200).tolist()

    res1 = evaluate_metric_uncertainty("RSK-01", fund_returns=synthetic_returns, iterations=100, seed=42)
    res2 = evaluate_metric_uncertainty("RSK-01", fund_returns=synthetic_returns, iterations=100, seed=42)

    assert res1.outcome == EvidenceOutcome.PASS
    assert res1.point_estimate is not None
    assert res1.standard_error is not None
    assert res1.ci_lower_95 is not None
    assert res1.ci_upper_95 is not None
    assert res1.ci_lower_95 < res1.ci_upper_95

    # Deterministic bitwise reproducibility
    assert res1.standard_error == res2.standard_error
    assert res1.ci_lower_95 == res2.ci_lower_95
    assert res1.ci_upper_95 == res2.ci_upper_95


def test_dependence_block_length_heuristic():
    # 1. Periodic dependent series (N=100): reaches asymptotic consistency bound floor(N^(1/3)) = 4
    returns_dep = [0.01, -0.02, 0.015, -0.01, 0.005] * 20
    b_len, rationale = estimate_dependence_block_length(returns_dep)
    assert b_len == 4
    assert "Bartlett 95% threshold" in rationale
    assert "floor(N^(1/3))" in rationale

    # 2. Independent white noise series (N=750): lag-1 autocorrelation is inside Bartlett bounds -> b=1
    rng = np.random.default_rng(999)
    returns_iid = rng.normal(0.0005, 0.01, size=750).tolist()
    b_len_iid, rationale_iid = estimate_dependence_block_length(returns_iid)
    assert b_len_iid == 1
    assert "selects b=1" in rationale_iid


# ============================================================================
# 7. Edge-Case Robustness Suite Execution Tests
# ============================================================================

def test_all_pre_specified_edge_cases():
    edge_results = run_all_edge_case_tests()
    assert len(edge_results) >= 12

    for r in edge_results:
        assert r.passed is True, f"Edge case failed: {r.metric_code} / {r.scenario_name}: {r.observed_behavior}"


# ============================================================================
# 8. Descriptive Dependency Analysis Tests
# ============================================================================

def test_descriptive_dependency_mapping():
    for code in ("RET-03", "RSK-01", "RSK-03", "RSK-06", "RAT-01", "REL-01"):
        dep = get_descriptive_dependency(code)
        assert dep.metric_code == code
        assert len(dep.mathematical_inputs) >= 1
        assert len(dep.structural_relationships) >= 1
        assert dep.governance_prohibitions_confirmed is True


# ============================================================================
# 9. Full Harness Execution & Deterministic Reproducibility Tests
# ============================================================================

def test_harness_full_suite_execution_and_reproducibility():
    harness = Phase2SAValidationHarness(seed=42)
    summaries1 = harness.execute_phase2s_a_full_suite()
    summaries2 = harness.execute_phase2s_a_full_suite()

    assert len(summaries1) == 6
    for code in ("RET-03", "RSK-01", "RSK-03", "RSK-06", "RAT-01", "REL-01"):
        s1 = summaries1[code]
        s2 = summaries2[code]

        assert s1.overall_outcome in (EvidenceOutcome.PASS, EvidenceOutcome.CONDITIONAL)
        assert s1.overall_outcome == s2.overall_outcome
        assert len(s1.records) == len(s2.records)

        # Assert zero governance transitions
        assert s1.overall_outcome.value not in ("VALIDATED", "APPROVED")


def test_dossier_generation_output_contracts():
    harness = Phase2SAValidationHarness(seed=42)
    summaries = harness.execute_phase2s_a_full_suite()

    md = generate_dossier_markdown(summaries, input_digest="TEST_SHA256_HASH")
    assert "# PHASE 2S-A VALIDATION EVIDENCE DOSSIER" in md
    assert "PHASE 2R CLOSED" in md
    assert "GOVERNANCE TRANSITION: NONE (ZERO AUTOMATIC TRANSITIONS)" in md
    assert "| **`RET-03`** |" in md
    assert "| **`RSK-01`** |" in md

    js = generate_dossier_json(summaries, input_digest="TEST_SHA256_HASH")
    assert '"phase": "2S-A"' in js
    assert '"input_digest": "TEST_SHA256_HASH"' in js
