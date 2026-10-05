"""
YUKIRA REL-03 Dispatcher & Kernel Tests (Annualized Mean Active Return, 3Y).

REL-03 is the paired-return decomposition required by the Benchmark
Relationship panel: it reports the annualised mean of the synchronous
daily active return series (R_p - R_b).

Scope covered here:
1. Kernel correctness (mean of paired active returns, divisor N).
2. Annualisation is a LINEAR multiplier on the mean, deliberately NOT the
   sqrt(periods_per_year) dispersion scaling used by tracking error.
3. A zero active return is a VALID measurement (0.0), not a zero-division
   error and not "insufficient data". REL-03 has no denominator.
4. Dispatcher normal case, and exact equality with the pure kernel.
5. INSUFFICIENT_DATA below the 700 paired-observation gate; numeric_value
   is NULL and is never fabricated as 0.0.
6. Missing / non-overlapping benchmark series yields INSUFFICIENT_DATA
   rather than a positionally mis-aligned number.
7. Alignment: date-keyed intersection (synchronous pairing), not
   positional zip; order-independent.
8. Resolution contract: REL-03 consumes the exact same aligned pair as
   REL-01 (Tracking Error) and REL-02 (Jensen's Alpha), so it adds no
   separate or looser data-resolution path.
9. Non-regression: REL-01 (Tracking Error) / REL-02 (Jensen's Alpha)
   values are unchanged by REL-03's presence in the same request. These
   are the Phase 2S scope-lock / CR-04 registry identities; REL-01 and
   REL-02 must never dispatch Pearson correlation or R-squared.

All metrics here remain CANDIDATE methodology. Implementation is not
validation and is not approval.
"""

from __future__ import annotations

import datetime
import math

import pytest

from src import active_return as active_return_mod
from src import alpha as alpha_mod
from src import beta as beta_mod
from src import tracking_error as te_mod
from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem

START = datetime.date(2021, 1, 15)


def _dates(n: int) -> list[str]:
    return [(START + datetime.timedelta(days=i)).isoformat() for i in range(n)]


def _obs(date: str, value: float) -> ObservationItem:
    return ObservationItem(
        effective_date=date,
        value=value,
        availability_time=f"{date}T18:00:00+05:30",
        revision_seq=1,
    )


def _build_series(n: int, nav_values=None, bench_values=None):
    """Build deterministic NAV and benchmark ObservationItems of length n."""
    dates = _dates(n)
    if nav_values is None:
        nav_values = _default_nav(n)
    if bench_values is None:
        bench_values = _default_bench(n)

    nav_series = [_obs(d, v) for d, v in zip(dates, nav_values)]
    bench_series = [_obs(d, v) for d, v in zip(dates, bench_values)]
    return dates, nav_series, bench_series


def _default_nav(n: int) -> list[float]:
    """Fund NAV path: benchmark-like with a deterministic active tilt."""
    values = [100.0]
    for i in range(n - 1):
        step = 0.004 * math.sin(i * 0.37) + 0.003 * math.cos(i * 0.11) + 0.0004
        values.append(values[-1] * (1.0 + step))
    return values


def _default_bench(n: int) -> list[float]:
    values = [1000.0]
    for i in range(n - 1):
        step = 0.004 * math.sin(i * 0.37) + 0.003 * math.cos(i * 0.11)
        values.append(values[-1] * (1.0 + step))
    return values


def _request(nav_series, bench_series=None, metric_codes=None, **kwargs) -> CalculationRequest:
    as_of = nav_series[-1].effective_date
    payload = {
        "request_id": "REQ-REL06",
        "as_of_date": as_of,
        "knowledge_cutoff_time": f"{as_of}T23:59:59+05:30",
        "metric_codes": metric_codes or ["REL-03"],
        "nav_series": nav_series,
    }
    if bench_series is not None:
        payload["benchmark_series"] = bench_series
    payload.update(kwargs)
    return CalculationRequest(**payload)


def _periodic(values: list[float]) -> list[float]:
    return [values[i + 1] / values[i] - 1.0 for i in range(len(values) - 1)]


# ---------------------------------------------------------------------------
# 1. Kernel: mean of paired active returns
# ---------------------------------------------------------------------------


def test_mean_active_return_matches_hand_computed_decomposition():
    # active = [0.005, 0.0, 0.0, 0.02] -> mean = 0.025 / 4 = 0.00625
    portfolio = [0.01, -0.02, 0.0, 0.03]
    benchmark = [0.005, -0.02, 0.0, 0.01]

    result = active_return_mod.mean_active_return(portfolio, benchmark)

    assert result == pytest.approx(0.00625, abs=1e-15)


def test_mean_active_return_uses_divisor_n_not_n_minus_one():
    # active = [0.01, 0.01] -> mean = 0.02 / 2 = 0.01. A sample-stdev style
    # (N-1) divisor would yield 0.02. The kernel must divide by N.
    result = active_return_mod.mean_active_return([0.02, 0.00], [0.01, -0.01])
    assert result == pytest.approx(0.01, abs=1e-15)


def test_mean_active_return_negative_when_fund_underperforms():
    result = active_return_mod.mean_active_return([0.00, 0.00], [0.01, 0.02])
    assert result == pytest.approx(-0.015, abs=1e-15)


# ---------------------------------------------------------------------------
# 2. Kernel: linear annualisation, NOT sqrt dispersion scaling
# ---------------------------------------------------------------------------


def test_annualized_uses_linear_multiplier_on_mean_not_sqrt():
    portfolio = [0.02, -0.01, 0.005, 0.03]
    benchmark = [0.01, -0.02, 0.001, 0.015]

    mean_active = active_return_mod.mean_active_return(portfolio, benchmark)
    annualized = active_return_mod.annualized_mean_active_return(
        portfolio, benchmark, 252.0
    )

    assert annualized == pytest.approx(mean_active * 252.0, abs=1e-15)

    # Explicitly refute the tracking-error style sqrt scaling. If the
    # implementation ever regressed to sqrt(252) the two would diverge.
    sqrt_scaled = mean_active * math.sqrt(252.0)
    assert not math.isclose(annualized, sqrt_scaled, rel_tol=1e-6)


def test_annualized_scales_linearly_with_periods_per_year():
    portfolio = [0.02, -0.01, 0.005, 0.03]
    benchmark = [0.01, -0.02, 0.001, 0.015]

    at_252 = active_return_mod.annualized_mean_active_return(portfolio, benchmark, 252.0)
    at_12 = active_return_mod.annualized_mean_active_return(portfolio, benchmark, 12.0)

    assert at_12 == pytest.approx(at_252 * (12.0 / 252.0), abs=1e-15)


# ---------------------------------------------------------------------------
# 3. Kernel: zero active return is a valid measurement
# ---------------------------------------------------------------------------


def test_zero_active_return_is_valid_zero_not_an_error():
    portfolio = [0.01, -0.02, 0.0, 0.03]
    benchmark = list(portfolio)

    assert active_return_mod.mean_active_return(portfolio, benchmark) == 0.0
    # Unlike the Information Ratio, REL-03 has no denominator: a fund that
    # exactly tracks its benchmark yields a measurable 0.0, not a division
    # by zero and not a "not available" state.
    assert (
        active_return_mod.annualized_mean_active_return(portfolio, benchmark, 252.0)
        == 0.0
    )


def test_flat_active_return_annualizes_to_constant_tilt():
    # Constant 10 bps/day active tilt -> 0.001 * 252 = 0.252.
    portfolio = [0.0] * 5
    benchmark = [-0.001] * 5
    result = active_return_mod.annualized_mean_active_return(portfolio, benchmark, 252.0)
    assert result == pytest.approx(0.252, abs=1e-15)


# ---------------------------------------------------------------------------
# 4. Kernel: input validation
# ---------------------------------------------------------------------------


def test_kernel_rejects_mismatched_series_lengths():
    with pytest.raises(ValueError, match="equal length"):
        active_return_mod.mean_active_return([0.01, 0.02], [0.01])


def test_kernel_rejects_empty_series():
    with pytest.raises(ValueError, match="at least one paired return"):
        active_return_mod.mean_active_return([], [])


@pytest.mark.parametrize("bad", [float("nan"), float("inf"), float("-inf")])
def test_kernel_rejects_non_finite_values(bad):
    with pytest.raises(ValueError):
        active_return_mod.mean_active_return([0.01, bad], [0.01, 0.01])
    with pytest.raises(ValueError):
        active_return_mod.mean_active_return([0.01, 0.01], [0.01, bad])


@pytest.mark.parametrize("bad_ppy", [0, -1.0, float("nan"), float("inf")])
def test_annualized_rejects_non_positive_periods_per_year(bad_ppy):
    with pytest.raises(ValueError):
        active_return_mod.annualized_mean_active_return([0.01, 0.02], [0.01, 0.01], bad_ppy)


def test_annualized_rejects_non_numeric_periods_per_year():
    with pytest.raises(TypeError):
        active_return_mod.annualized_mean_active_return(
            [0.01, 0.02], [0.01, 0.01], "252"
        )


# ---------------------------------------------------------------------------
# 5. Dispatcher: normal case
# ---------------------------------------------------------------------------


def test_rel06_dispatch_normal_case_matches_pure_kernel_exactly():
    _, nav_series, bench_series = _build_series(740)

    items = dispatch_calculation(_request(nav_series, bench_series))
    assert len(items) == 1
    item = items[0]

    assert item.metric_code == "REL-03"
    assert item.status == CalculationStatus.CALCULATED
    assert item.period_type == "3Y"
    assert item.units == "PERCENTAGE"
    assert item.numeric_value is not None

    fund_returns = _periodic([o.value for o in nav_series])
    bench_returns = _periodic([o.value for o in bench_series])
    expected = active_return_mod.annualized_mean_active_return(
        fund_returns, bench_returns, 252.0
    )
    assert abs(item.numeric_value - expected) < 1e-12

    diagnostics = item.diagnostics
    assert diagnostics["paired_count"] == 739
    assert diagnostics["min_paired_observations"] == 700
    assert diagnostics["periods_per_year"] == 252.0
    assert diagnostics["annualization"] == "LINEAR_MULTIPLIER_252_ON_MEAN"
    assert diagnostics["risk_free_required"] is False
    assert diagnostics["methodology_status"] == "CANDIDATE"
    assert diagnostics["mean_daily_active_return"] == pytest.approx(
        expected / 252.0, abs=1e-15
    )


def test_rel06_dispatch_known_value_regression_anchor():
    # Fully hand-checkable 4-return series.
    nav_series = [_obs(d, v) for d, v in zip(
        _dates(5), [100.0, 101.0, 99.0, 99.0, 100.98]
    )]
    bench_series = [_obs(d, v) for d, v in zip(
        _dates(5), [200.0, 204.0, 200.0, 200.0, 204.0]
    )]

    item = dispatch_calculation(
        _request(nav_series, bench_series, parameters={"min_paired_observations": 4})
    )[0]

    assert item.status == CalculationStatus.CALCULATED
    # active = [-0.01, -0.00019413706076487625, 0.0, 0.0]
    # mean  = -0.0025485342651912213
    # * 252 = -0.6422306348281878
    assert item.numeric_value == pytest.approx(-0.6422306348281878, abs=1e-12)
    assert item.diagnostics["paired_count"] == 4


def test_rel06_dispatch_respects_explicit_periods_per_year():
    _, nav_series, bench_series = _build_series(740)

    daily = dispatch_calculation(_request(nav_series, bench_series))[0]
    monthly = dispatch_calculation(
        _request(nav_series, bench_series, parameters={"periods_per_year": 12.0})
    )[0]

    assert monthly.diagnostics["periods_per_year"] == 12.0
    assert monthly.numeric_value == pytest.approx(
        daily.numeric_value * (12.0 / 252.0), abs=1e-15
    )


# ---------------------------------------------------------------------------
# 6. Dispatcher: insufficient data and missing inputs
# ---------------------------------------------------------------------------


def test_rel06_insufficient_below_min_paired_observations():
    _, nav_series, bench_series = _build_series(650)

    item = dispatch_calculation(_request(nav_series, bench_series))[0]

    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
    # Anti-fabrication contract: NULL is reported, never 0.0.
    assert item.numeric_value != 0.0
    assert "minimum 700 required" in item.error_message
    assert item.diagnostics["paired_count"] == 649
    assert item.diagnostics["min_paired_observations"] == 700


def test_rel06_missing_benchmark_series_is_insufficient():
    _, nav_series, _ = _build_series(740)

    item = dispatch_calculation(_request(nav_series, benchmark_series=None))[0]

    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
    assert "Aligned benchmark return series required" in item.error_message


def test_rel06_non_overlapping_benchmark_dates_are_insufficient_not_misaligned():
    """A benchmark that shares no date with the fund must not be paired positionally."""
    dates = _dates(740)
    fund_dates = dates
    bench_dates = [(START + datetime.timedelta(days=2000 + i)).isoformat() for i in range(740)]

    nav_series = [_obs(d, v) for d, v in zip(fund_dates, _default_nav(740))]
    bench_series = [_obs(d, v) for d, v in zip(bench_dates, _default_bench(740))]

    item = dispatch_calculation(_request(nav_series, bench_series))[0]

    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None
    assert "Aligned benchmark return series required" in item.error_message


def test_rel06_zero_active_return_reports_calculated_zero():
    # Benchmark is exactly 2x the fund: identical percentage returns, so the
    # active return is a true 0.0 and must be reported as CALCULATED 0.0.
    nav_values = _default_nav(740)
    bench_values = [2.0 * v for v in nav_values]
    _, nav_series, bench_series = _build_series(740, nav_values, bench_values)

    item = dispatch_calculation(_request(nav_series, bench_series))[0]

    assert item.status == CalculationStatus.CALCULATED
    assert item.numeric_value == 0.0
    assert item.diagnostics["mean_daily_active_return"] == 0.0


# ---------------------------------------------------------------------------
# 7. Dispatcher: date-keyed alignment
# ---------------------------------------------------------------------------


def test_rel06_aligns_on_shared_dates_only():
    """Fund has 740 dates; benchmark only covers every other date."""
    dates = _dates(740)
    nav_values = _default_nav(740)
    bench_values = _default_bench(740)

    nav_series = [_obs(d, v) for d, v in zip(dates, nav_values)]
    # Benchmark observed on even-indexed dates only -> 370 shared dates.
    bench_series = [
        _obs(dates[i], bench_values[i]) for i in range(0, 740, 2)
    ]

    item = dispatch_calculation(
        _request(
            nav_series,
            bench_series,
            parameters={"min_paired_observations": 300},
        )
    )[0]

    assert item.status == CalculationStatus.CALCULATED
    assert item.diagnostics["paired_count"] == 369

    # Expected value must come from the shared-date pairing only.
    shared_indices = list(range(0, 740, 2))
    aligned_nav = [nav_values[i] for i in shared_indices]
    aligned_bench = [bench_values[i] for i in shared_indices]
    expected = active_return_mod.annualized_mean_active_return(
        _periodic(aligned_nav), _periodic(aligned_bench), 252.0
    )
    assert abs(item.numeric_value - expected) < 1e-12


def test_rel06_is_independent_of_input_series_ordering():
    _, nav_series, bench_series = _build_series(740)

    sorted_item = dispatch_calculation(_request(nav_series, bench_series))[0]

    # Deterministic reversal of the benchmark series.
    reversed_item = dispatch_calculation(
        _request(nav_series, list(reversed(bench_series)))
    )[0]

    assert sorted_item.status == CalculationStatus.CALCULATED
    assert reversed_item.status == CalculationStatus.CALCULATED
    assert sorted_item.diagnostics["paired_count"] == reversed_item.diagnostics["paired_count"] == 739
    assert sorted_item.numeric_value == pytest.approx(
        reversed_item.numeric_value, abs=1e-15
    )


# ---------------------------------------------------------------------------
# 8. Resolution contract + non-regression of existing relative metrics
# ---------------------------------------------------------------------------


def test_rel06_consumes_the_same_aligned_pair_as_rel02_and_rel03():
    """REL-03 must not introduce a separate or looser resolution path."""
    dates = _dates(740)
    nav_values = _default_nav(740)
    bench_values = _default_bench(740)
    nav_series = [_obs(d, v) for d, v in zip(dates, nav_values)]
    bench_series = [_obs(d, v) for d, v in zip(dates, bench_values)]

    items = dispatch_calculation(
        _request(nav_series, bench_series, metric_codes=["REL-01", "REL-02", "REL-03"])
    )
    code_map = {i.metric_code: i for i in items}
    assert set(code_map) == {"REL-01", "REL-02", "REL-03"}

    fund_returns = _periodic(nav_values)
    bench_returns = _periodic(bench_values)

    # Identical aligned observation set across the two paired-gated metrics.
    assert code_map["REL-01"].diagnostics["paired_count"] == 739
    assert code_map["REL-03"].diagnostics["paired_count"] == 739
    assert (
        code_map["REL-01"].diagnostics["paired_count"]
        == code_map["REL-03"].diagnostics["paired_count"]
    )

    # REL-01 (Tracking Error) round-trips against the pure kernel on the same
    # aligned pair, and its active-return diagnostics must equal REL-03's,
    # proving both consumed the identical synchronous pairing.
    expected_te = te_mod.tracking_error(fund_returns, bench_returns, 252.0)
    assert code_map["REL-01"].numeric_value == pytest.approx(expected_te, abs=1e-12)
    assert code_map["REL-01"].diagnostics["mean_daily_active_return"] == pytest.approx(
        code_map["REL-03"].diagnostics["mean_daily_active_return"], abs=1e-15
    )
    assert code_map["REL-01"].diagnostics["annualized_mean_active_return"] == pytest.approx(
        code_map["REL-03"].numeric_value, abs=1e-12
    )

    # REL-02 (Jensen's Alpha) round-trips against the pure OLS kernel on the
    # same aligned pair (no risk-free series supplied -> raw-return model).
    beta_value = beta_mod.beta(fund_returns, bench_returns, risk_free_rates=None)
    expected_ja = alpha_mod.jensens_alpha_ols(
        fund_returns,
        bench_returns,
        risk_free_rates=None,
        portfolio_beta=beta_value,
        periods_per_year=252.0,
    )
    assert code_map["REL-02"].numeric_value == pytest.approx(expected_ja, abs=1e-12)
    assert code_map["REL-02"].diagnostics["beta"] == pytest.approx(beta_value, abs=1e-12)

    assert code_map["REL-03"].numeric_value == pytest.approx(
        active_return_mod.annualized_mean_active_return(fund_returns, bench_returns, 252.0),
        abs=1e-12,
    )


def test_rel06_does_not_require_a_risk_free_series():
    """Unlike REL-01, active return is an excess over the benchmark only."""
    _, nav_series, bench_series = _build_series(740)

    item = dispatch_calculation(_request(nav_series, bench_series, risk_free_series=[]))[0]

    assert item.status == CalculationStatus.CALCULATED
    assert item.diagnostics["risk_free_required"] is False


def test_rel06_presence_does_not_change_existing_relative_metric_values():
    """Non-regression: REL-01 / REL-02 are identical with and without REL-03."""
    nav_series = [_obs(d, v) for d, v in zip(
        _dates(5), [100.0, 102.0, 101.0, 103.0, 106.0]
    )]
    bench_series = [_obs(d, v) for d, v in zip(
        _dates(5), [200.0, 202.0, 201.0, 204.0, 207.0]
    )]
    params = {"min_paired_observations": 4}

    without = {
        i.metric_code: i
        for i in dispatch_calculation(
            _request(
                nav_series,
                bench_series,
                metric_codes=["REL-01", "REL-02"],
                parameters=dict(params),
            )
        )
    }
    with_rel06 = {
        i.metric_code: i
        for i in dispatch_calculation(
            _request(
                nav_series,
                bench_series,
                metric_codes=["REL-01", "REL-02", "REL-03"],
                parameters=dict(params),
            )
        )
    }

    # Values fixed by the restored registry identities: REL-01 = Tracking
    # Error and REL-02 = Jensen's Alpha, each round-tripped against its pure
    # kernel on the identical 4-pair aligned returns (gate lowered to 4 only
    # for this fixture; the frozen default floor is 700).
    fund_returns = _periodic([o.value for o in nav_series])
    bench_returns = _periodic([o.value for o in bench_series])
    expected_te = te_mod.tracking_error(fund_returns, bench_returns, 252.0)
    beta_value = beta_mod.beta(fund_returns, bench_returns, risk_free_rates=None)
    expected_ja = alpha_mod.jensens_alpha_ols(
        fund_returns,
        bench_returns,
        risk_free_rates=None,
        portfolio_beta=beta_value,
        periods_per_year=252.0,
    )
    assert without["REL-01"].numeric_value == pytest.approx(expected_te, abs=1e-12)
    assert without["REL-02"].numeric_value == pytest.approx(expected_ja, abs=1e-12)
    # REL-01 must never regress to a correlation ratio in this request either.
    assert without["REL-01"].units == "PERCENTAGE"
    assert without["REL-02"].units == "PERCENTAGE"
    for code in ("REL-01", "REL-02"):
        assert with_rel06[code].numeric_value == without[code].numeric_value
        assert with_rel06[code].status == without[code].status
        assert with_rel06[code].units == without[code].units
        assert with_rel06[code].diagnostics == without[code].diagnostics

    assert with_rel06["REL-03"].numeric_value == pytest.approx(
        1.5399412423832328, abs=1e-12
    )
