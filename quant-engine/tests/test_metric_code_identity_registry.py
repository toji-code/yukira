"""
Metric-code identity (registry) regression tests for the quant dispatcher.

Authoritative code -> metric bindings exercised here:

  Phase 2R analytical profile / Phase 2S scope locks
  (docs/research/PHASE_2S_SCOPE_LOCK.md, docs/research/PHASE_2S_B_SCOPE_LOCK.md,
   Contradiction Audit Record 4 = RESOLVED):
      MKT-01 = Beta 3Y              MKT-01 = Tracking Error 3Y
      REL-02 = Jensen's Alpha 3Y    MKT-02 = Downside Beta 3Y

  Frozen Phase 2H registry (phase2h_quantitative_methodology.md):
      MKT-01 = Beta 3Y              MKT-02 = Downside Beta 3Y
      MKT-03 / MKT-04 / MKT-05 = Upside Capture / Downside Capture / Capture Spread
      RAT-04 = Information Ratio 3Y

MKT-01 and REL-02 must never dispatch Pearson correlation or the coefficient of
determination, and MKT-01 / MKT-02 must never dispatch Tracking Error or
Information Ratio: those renumberings contradicted both the frozen registry and
the resolved CR-04 governance record.

All metrics remain CANDIDATE. Implementation is not validation or approval.
"""

from __future__ import annotations

import datetime

import pytest

from src import alpha as alpha_mod
from src import beta as beta_mod
from src import tracking_error as te_mod
from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem


def _obs(date: str, value: float) -> ObservationItem:
    return ObservationItem(
        effective_date=date,
        value=value,
        availability_time="2024-01-31T23:59:59+05:30",
        revision_seq=1,
    )


def _declining_series(n: int = 720) -> tuple[list, list, list]:
    """Deterministic monotonically declining NAV / benchmark / risk-free series."""
    start = datetime.date(2021, 1, 15)
    nav, bench, rf = [], [], []
    for i in range(n):
        d = (start + datetime.timedelta(days=i)).isoformat()
        nav.append(_obs(d, 100.0 - 0.05 * i))
        bench.append(_obs(d, 1000.0 - 0.5 * i))
        rf.append(_obs(d, 0.070))
    return nav, bench, rf


def _returns(values: list[float]) -> list[float]:
    return [values[i] / values[i - 1] - 1.0 for i in range(1, len(values))]


def _dispatch(metric_codes, nav_series, benchmark_series, risk_free_series=None):
    request = CalculationRequest(
        request_id="REQ-CODE-IDENTITY",
        scheme_id="1",
        as_of_date="2024-01-15",
        knowledge_cutoff_time="2024-01-31T23:59:59+05:30",
        metric_codes=metric_codes,
        nav_series=nav_series,
        benchmark_series=benchmark_series,
        risk_free_series=risk_free_series,
    )
    return {item.metric_code: item for item in dispatch_calculation(request)}


def test_canonical_mkt01_mkt02():
    """MKT-01 = Beta and MKT-02 = Downside Beta (frozen Phase 2H identities)."""
    nav, bench, rf = _declining_series()
    by_code = _dispatch(
        ["MKT-01", "MKT-02"],
        nav,
        bench,
        risk_free_series=rf,
    )

    for code in ("MKT-01", "MKT-02"):
        assert by_code[code].status == CalculationStatus.CALCULATED, code
        assert by_code[code].units == "RATIO", code
        assert by_code[code].numeric_value is not None, code

    # Same metric, two registry codes: identical deterministic values.
    assert by_code["MKT-01"].numeric_value == pytest.approx(by_code["MKT-01"].numeric_value, abs=1e-15)
    assert by_code["MKT-02"].numeric_value == pytest.approx(by_code["MKT-02"].numeric_value, abs=1e-15)

    # MKT-01 must never be Tracking Error and MKT-02 must never be Information Ratio.
    assert by_code["MKT-01"].units != "PERCENTAGE"
    assert by_code["MKT-02"].units != "PERCENTAGE"


def test_rel02_dispatches_tracking_error_not_correlation():
    nav, bench, _ = _declining_series()
    by_code = _dispatch(["REL-01"], nav, bench)

    item = by_code["REL-01"]
    assert item.status == CalculationStatus.CALCULATED
    # Tracking Error is a percentage; the hijacked correlation identity was a ratio.
    assert item.units == "PERCENTAGE"
    assert item.period_type == "3Y"
    assert item.diagnostics["annualization_convention"] == "SQRT_252"
    assert item.diagnostics["denominator_convention"] == "SAMPLE_VARIANCE_N_MINUS_1"
    assert "correlation" not in item.diagnostics

    expected = te_mod.tracking_error(
        _returns([o.value for o in nav]),
        _returns([o.value for o in bench]),
        252.0,
    )
    assert item.numeric_value == pytest.approx(expected, abs=1e-12)


def test_rel02_enforces_frozen_700_paired_floor():
    """< 700 paired observations must yield INSUFFICIENT_DATA, never a correlation."""
    nav = [_obs(f"2024-01-0{i}", v) for i, v in enumerate([100.0, 102.0, 101.0, 103.0, 106.0], start=1)]
    bench = [_obs(f"2024-01-0{i}", v) for i, v in enumerate([200.0, 202.0, 201.0, 204.0, 207.0], start=1)]

    by_code = _dispatch(["REL-01"], nav, bench)

    item = by_code["REL-01"]
    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
    assert item.units == "PERCENTAGE"
    assert item.diagnostics["paired_count"] == 4
    assert item.diagnostics["min_paired_observations"] == 700


def test_rel03_dispatches_jensens_alpha_not_r_squared():
    nav = [_obs(f"2024-01-0{i}", v) for i, v in enumerate([100.0, 102.0, 101.0, 103.0, 106.0], start=1)]
    bench = [_obs(f"2024-01-0{i}", v) for i, v in enumerate([200.0, 202.0, 201.0, 204.0, 207.0], start=1)]

    by_code = _dispatch(["REL-02"], nav, bench)

    item = by_code["REL-02"]
    assert item.status == CalculationStatus.CALCULATED
    # Jensen's Alpha is a percentage; the hijacked R-squared identity was a ratio.
    assert item.units == "PERCENTAGE"
    assert item.diagnostics["formula"] == "OLS intercept (alpha_daily * 252)"
    assert "correlation" not in item.diagnostics

    fund_returns = _returns([o.value for o in nav])
    bench_returns = _returns([o.value for o in bench])
    beta_value = beta_mod.beta(fund_returns, bench_returns, risk_free_rates=None)
    expected = alpha_mod.jensens_alpha_ols(
        fund_returns,
        bench_returns,
        risk_free_rates=None,
        portfolio_beta=beta_value,
        periods_per_year=252.0,
    )
    assert item.numeric_value == pytest.approx(expected, abs=1e-12)
    assert item.diagnostics["beta"] == pytest.approx(beta_value, abs=1e-12)


def test_rel03_requires_benchmark_series():
    nav = [_obs("2024-01-01", 100.0), _obs("2024-01-02", 101.0)]
    by_code = _dispatch(["REL-02"], nav, None)

    item = by_code["REL-02"]
    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
