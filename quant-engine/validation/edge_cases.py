"""Edge-case robustness and failure mode evaluation for Phase 2S-A.

CORE EPISTEMIC DIRECTIVE:
Expected edge behavior must be explicit:
- Valid mathematical result (e.g. 0.0 drawdown on increasing series)
- Controlled insufficient-evidence result (e.g. INSUFFICIENT_DATA status)
- Controlled validation failure or explicit exception
NEVER silently coerce invalid financial observations into an artificial result.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from validation.models import EvidenceOutcome, ValidationRecord, ValidationTestType
from validation.reference_kernels import (
    ref_cagr,
    ref_volatility,
    ref_maximum_drawdown,
    ref_historical_var,
    ref_sharpe_ratio,
    ref_beta,
)


@dataclass
class EdgeCaseTestResult:
    """Report of a specific edge case scenario evaluation."""
    metric_code: str
    scenario_name: str
    description: str
    expected_behavior: str
    observed_behavior: str
    passed: bool
    outcome: EvidenceOutcome

    def to_record(self) -> ValidationRecord:
        return ValidationRecord(
            metric_code=self.metric_code,
            test_type=ValidationTestType.EDGE_CASE_ROBUSTNESS,
            outcome=self.outcome,
            diagnostics={
                "scenario_name": self.scenario_name,
                "description": self.description,
                "expected": self.expected_behavior,
                "observed": self.observed_behavior,
                "passed": self.passed,
            },
        )


def _evaluate_exception_scenario(
    metric_code: str,
    scenario_name: str,
    description: str,
    fn: Callable[[], Any],
    expected_exception_type: type[Exception],
) -> EdgeCaseTestResult:
    try:
        val = fn()
        return EdgeCaseTestResult(
            metric_code=metric_code,
            scenario_name=scenario_name,
            description=description,
            expected_behavior=f"Raise {expected_exception_type.__name__}",
            observed_behavior=f"Returned unexpected value {val} without error",
            passed=False,
            outcome=EvidenceOutcome.BLOCKED,
        )
    except expected_exception_type as ex:
        return EdgeCaseTestResult(
            metric_code=metric_code,
            scenario_name=scenario_name,
            description=description,
            expected_behavior=f"Raise {expected_exception_type.__name__}",
            observed_behavior=f"Explicitly rejected with {type(ex).__name__}: {str(ex)[:100]}",
            passed=True,
            outcome=EvidenceOutcome.PASS,
        )
    except Exception as ex:
        return EdgeCaseTestResult(
            metric_code=metric_code,
            scenario_name=scenario_name,
            description=description,
            expected_behavior=f"Raise {expected_exception_type.__name__}",
            observed_behavior=f"Raised unexpected exception {type(ex).__name__}: {str(ex)[:100]}",
            passed=False,
            outcome=EvidenceOutcome.BLOCKED,
        )


def _evaluate_valid_scenario(
    metric_code: str,
    scenario_name: str,
    description: str,
    fn: Callable[[], Any],
    expected_value: Any,
    comparator: Callable[[Any, Any], bool] = lambda a, b: a == b,
) -> EdgeCaseTestResult:
    try:
        val = fn()
        passed = comparator(val, expected_value)
        return EdgeCaseTestResult(
            metric_code=metric_code,
            scenario_name=scenario_name,
            description=description,
            expected_behavior=f"Valid result: {expected_value}",
            observed_behavior=f"Observed result: {val}",
            passed=passed,
            outcome=EvidenceOutcome.PASS if passed else EvidenceOutcome.BLOCKED,
        )
    except Exception as ex:
        return EdgeCaseTestResult(
            metric_code=metric_code,
            scenario_name=scenario_name,
            description=description,
            expected_behavior=f"Valid result: {expected_value}",
            observed_behavior=f"Unexpected exception {type(ex).__name__}: {str(ex)[:100]}",
            passed=False,
            outcome=EvidenceOutcome.BLOCKED,
        )


def run_all_edge_case_tests() -> list[EdgeCaseTestResult]:
    """Execute all pre-specified edge-case tests across the 6 representative metrics."""
    results: list[EdgeCaseTestResult] = []

    # 1. RET-03 Edge Cases
    results.append(_evaluate_exception_scenario(
        metric_code="RET-03",
        scenario_name="negative_start_nav",
        description="Non-positive start NAV rejection",
        fn=lambda: ref_cagr(-10.0, 100.0, 365),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="RET-03",
        scenario_name="zero_elapsed_days",
        description="Zero calendar days elapsed rejection",
        fn=lambda: ref_cagr(100.0, 110.0, 0),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_valid_scenario(
        metric_code="RET-03",
        scenario_name="flat_nav_series",
        description="Identical start and end NAV over Julian year yields 0.0 CAGR",
        fn=lambda: ref_cagr(100.0, 100.0, 365.25),
        expected_value=0.0,
        comparator=lambda a, b: abs(a - b) < 1e-12,
    ))

    # 2. RSK-01 Edge Cases
    results.append(_evaluate_exception_scenario(
        metric_code="RSK-01",
        scenario_name="insufficient_returns",
        description="Single return observation rejection (N < 2)",
        fn=lambda: ref_volatility([0.05]),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_valid_scenario(
        metric_code="RSK-01",
        scenario_name="constant_returns_zero_vol",
        description="Constant returns yield valid 0.0 volatility",
        fn=lambda: ref_volatility([0.01, 0.01, 0.01, 0.01]),
        expected_value=0.0,
        comparator=lambda a, b: abs(a - b) < 1e-12,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="RSK-01",
        scenario_name="nan_in_returns",
        description="Rejection of non-finite NaN return observations",
        fn=lambda: ref_volatility([0.01, float("nan"), 0.02]),
        expected_exception_type=ValueError,
    ))

    # 3. RSK-03 Edge Cases
    results.append(_evaluate_valid_scenario(
        metric_code="RSK-03",
        scenario_name="monotonically_increasing_nav",
        description="Monotonically increasing NAV series yields 0.0 maximum drawdown",
        fn=lambda: ref_maximum_drawdown([100.0, 105.0, 110.0, 115.0]),
        expected_value=0.0,
        comparator=lambda a, b: abs(a - b) < 1e-12,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="RSK-03",
        scenario_name="empty_nav_series",
        description="Empty NAV series rejection",
        fn=lambda: ref_maximum_drawdown([]),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="RSK-03",
        scenario_name="negative_nav_in_series",
        description="Negative NAV observation rejection",
        fn=lambda: ref_maximum_drawdown([100.0, -5.0, 102.0]),
        expected_exception_type=ValueError,
    ))

    # 4. RSK-06 Edge Cases
    results.append(_evaluate_exception_scenario(
        metric_code="RSK-06",
        scenario_name="single_observation_var",
        description="Single observation rejection for quantile",
        fn=lambda: ref_historical_var([0.01]),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_valid_scenario(
        metric_code="RSK-06",
        scenario_name="all_positive_returns",
        description="All positive returns yield negative VaR (gain magnitude)",
        fn=lambda: ref_historical_var([0.02, 0.03, 0.04, 0.05, 0.06]),
        expected_value=0.0,
        comparator=lambda a, b: a < 0.0,  # Negative VaR indicates lower-tail cutoff is a gain
    ))

    # 5. RAT-01 Edge Cases
    results.append(_evaluate_exception_scenario(
        metric_code="RAT-01",
        scenario_name="zero_excess_return_variance",
        description="Zero excess return variance yields undefined Sharpe ratio",
        fn=lambda: ref_sharpe_ratio([0.0002, 0.0002, 0.0002], 0.0002),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="RAT-01",
        scenario_name="risk_free_length_mismatch",
        description="Length mismatch between returns and risk-free series",
        fn=lambda: ref_sharpe_ratio([0.01, 0.02], [0.0001]),
        expected_exception_type=ValueError,
    ))

    # 6. REL-01 Edge Cases
    results.append(_evaluate_exception_scenario(
        metric_code="REL-01",
        scenario_name="zero_benchmark_variance",
        description="Constant benchmark yields undefined Beta",
        fn=lambda: ref_beta([0.01, -0.02, 0.03], [0.00, 0.00, 0.00]),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_exception_scenario(
        metric_code="REL-01",
        scenario_name="portfolio_benchmark_length_mismatch",
        description="Length mismatch between portfolio and benchmark",
        fn=lambda: ref_beta([0.01, 0.02], [0.01]),
        expected_exception_type=ValueError,
    ))
    results.append(_evaluate_valid_scenario(
        metric_code="REL-01",
        scenario_name="orthogonal_uncorrelated_zero_beta",
        description="Orthogonal benchmark movement yields 0.0 Beta",
        fn=lambda: ref_beta([0.01, -0.01, 0.01, -0.01], [0.01, 0.01, -0.01, -0.01]),
        expected_value=0.0,
        comparator=lambda a, b: abs(a - b) < 1e-12,
    ))

    return results
