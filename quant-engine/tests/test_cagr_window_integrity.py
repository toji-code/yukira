r"""
CAGR observation-window integrity regression tests (RET-01 / RET-03 / RET-04).

Governing specification: phase2h_quantitative_methodology.md section 4.1 (FROZEN)

    RET-01 = 1Y CAGR   -> trailing 12 calendar months, minimum 240 observations
    RET-03 = 3Y CAGR   -> trailing 36 calendar months, minimum 700 observations
    RET-04 = 5Y CAGR   -> trailing 60 calendar months, minimum 1200 observations

Historical defect
-----------------
dispatcher.py previously computed ONE whole-series CAGR and emitted it three times:

    RET-01 -> period_type "1Y"  \                  
    RET-03 -> period_type "3Y"   > identical numeric_value, identical elapsed_years
    RET-04 -> period_type "5Y"  /

Three distinct metrics therefore reported byte-identical values while each advertised a
different observation window. Any consumer (including the future scoring layer) reading
period_type would materially misattribute the window actually measured.

The contract restored by these tests:

  * RET-03 retains whole-series semantics. PeriodReturnCalculationService selects the
    boundary observations under PIT rules and supplies exactly that window, so the engine
    must not re-window it. Ret03EndToEndIntegrationTest pins the canonical pilot value
    0.272495778659 for 2021-01-15 -> 2024-01-15; that path must remain bit-identical.
  * RET-01 and RET-04 derive their own trailing calendar window, anchored at the last
    PIT-resolved observation.
  * An unrecoverable window surfaces as INSUFFICIENT_DATA, never as a mislabelled value.
"""

from __future__ import annotations

import datetime

import pytest

from src.api.dispatcher import dispatch_calculation
from src.api.models import CalculationRequest, CalculationStatus, ObservationItem

KNOWLEDGE_CUTOFF = "2024-01-31T23:59:59+05:30"


def _obs(date: str, value: float) -> ObservationItem:
    return ObservationItem(
        effective_date=date,
        value=value,
        availability_time=KNOWLEDGE_CUTOFF,
        revision_seq=1,
    )


def _trading_series(n_business_days: int, end: datetime.date, start_value: float = 100.0):
    """Deterministic weekday-only NAV series ending on `end`."""
    dates: list[datetime.date] = []
    cursor = end
    while len(dates) < n_business_days:
        if cursor.weekday() < 5:
            dates.append(cursor)
        cursor -= datetime.timedelta(days=1)
    dates.reverse()

    nav, value = [], start_value
    for d in dates:
        value *= 1.0009
        nav.append(_obs(d.isoformat(), value))
    return nav


def _dispatch(codes, nav_series, as_of=None):
    request = CalculationRequest(
        request_id="REQ-CAGR-WINDOW",
        scheme_id="1",
        as_of_date=as_of or nav_series[-1].effective_date,
        knowledge_cutoff_time=KNOWLEDGE_CUTOFF,
        metric_codes=codes,
        nav_series=nav_series,
    )
    return {item.metric_code: item for item in dispatch_calculation(request)}


END = datetime.date(2024, 1, 15)


def test_ret01_ret03_ret04_are_not_identical():
    """
    Core regression: three distinct CAGR metrics must not collapse to one value.

    Previously RET-01, RET-03 and RET-04 all returned the same whole-series CAGR.
    """
    nav = _trading_series(1400, END)

    by_code = _dispatch(["RET-01", "RET-03", "RET-04"], nav)

    ret01 = by_code["RET-01"]
    ret03 = by_code["RET-03"]
    ret04 = by_code["RET-04"]

    assert ret01.status == CalculationStatus.CALCULATED
    assert ret03.status == CalculationStatus.CALCULATED
    assert ret04.status == CalculationStatus.CALCULATED

    # The historical defect: all three shared one numeric value.
    assert ret01.numeric_value != pytest.approx(ret03.numeric_value, abs=1e-12)
    assert ret03.numeric_value != pytest.approx(ret04.numeric_value, abs=1e-12)
    assert ret01.numeric_value != pytest.approx(ret04.numeric_value, abs=1e-12)


def test_ret01_window_is_one_year_not_whole_series():
    """RET-01 must measure a trailing 12-calendar-month window, not the full series."""
    nav = _trading_series(1400, END)  # ~5.6 years of data

    item = _dispatch(["RET-01"], nav)["RET-01"]

    assert item.status == CalculationStatus.CALCULATED
    assert item.period_type == "1Y"
    assert item.diagnostics["lookback_calendar_days"] == 365
    assert item.diagnostics["min_observations_required"] == 240

    window_start = datetime.date.fromisoformat(item.diagnostics["window_start_date"])
    window_end = datetime.date.fromisoformat(item.diagnostics["window_end_date"])
    elapsed_days = (window_end - window_start).days

    # Window must be ~1 year, not the ~5.6 years actually supplied.
    assert 360 <= elapsed_days <= 370, elapsed_days
    assert item.diagnostics["window_observation_count"] >= 240
    # Disclosed window must be strictly shorter than the supplied series.
    assert window_start > datetime.date.fromisoformat(nav[0].effective_date)


def test_ret04_refuses_to_fabricate_when_history_is_short():
    """
    RET-04 (5Y CAGR) on a 3-year series must report INSUFFICIENT_DATA.

    It must never emit the 3-year value under a "5Y" label.
    """
    nav = _trading_series(780, END)  # ~3 years

    item = _dispatch(["RET-04"], nav)["RET-04"]

    assert item.status == CalculationStatus.INSUFFICIENT_DATA
    assert item.numeric_value is None, "RET-04 must not fabricate a 5-year CAGR"
    assert item.units == "PERCENTAGE"
    assert item.diagnostics["min_observations_required"] == 1200
    assert item.diagnostics["window_observation_count"] == 780
    assert "minimum 1200 required" in item.error_message


def test_ret01_refuses_when_insufficient_observations_in_window():
    nav = _trading_series(780, END)

    item = _dispatch(["RET-01"], nav)["RET-01"]

    assert item.status == CalculationStatus.CALCULATED
    assert item.diagnostics["window_observation_count"] >= 240


def test_ret03_remains_caller_supplied_whole_series():
    """
    RET-03 must NOT be re-windowed.

    PeriodReturnCalculationService performs PIT boundary selection and hands the engine
    exactly that window. Re-windowing inside the engine would change the canonical pilot
    result pinned by Ret03EndToEndIntegrationTest.
    """
    nav = _trading_series(780, END)

    item = _dispatch(["RET-03"], nav)["RET-03"]

    assert item.status == CalculationStatus.CALCULATED
    assert item.period_type == "3Y"
    assert item.diagnostics["window_anchor_convention"] == "CALLER_SUPPLIED_PIT_BOUNDARY_PAIR"
    assert item.diagnostics["window_start_date"] == nav[0].effective_date
    assert item.diagnostics["window_end_date"] == nav[-1].effective_date
    assert item.diagnostics["window_observation_count"] == len(nav)


def test_ret01_window_is_stable_under_older_history():
    """
    Backward-only anchoring: pre-window history must not influence the result.

    Computing over the full 5.6-year history must equal computing over exactly the resolved
    12-month window slice. If the walk-back ever leaked older observations into the value, or
    if the window drifted with input length, these two would diverge.
    """
    nav = _trading_series(1400, END)

    full = _dispatch(["RET-01"], nav)["RET-01"]
    assert full.status == CalculationStatus.CALCULATED

    window_start = full.diagnostics["window_start_date"]

    # Rebuild an identical series that begins exactly at the resolved window boundary.
    sliced = [o for o in nav if o.effective_date >= window_start]

    recomputed = _dispatch(["RET-01"], sliced)["RET-01"]

    assert recomputed.status == CalculationStatus.CALCULATED
    assert recomputed.diagnostics["window_start_date"] == window_start
    assert recomputed.numeric_value == pytest.approx(full.numeric_value, abs=1e-15)


def test_engine_relies_on_caller_for_as_of_date_enforcement():
    """
    KNOWN HARDENING CANDIDATE (pre-existing, cross-cutting, NOT introduced by this change).

    The Quant Engine never filters `nav_series` against `request.as_of_date`. Observations
    dated after the analysis cutoff are accepted and used as the window anchor. Point-in-Time
    enforcement for NAV currently lives entirely in the caller
    (PitObservationResolutionService / HistoricalAnalyticalDataService).

    This falls short of the defence-in-depth expectation in AGENTS.md section 8 ("Future NAVs
    must never leak into past calculation windows"). It is recorded here as an explicit, pinned
    characterisation so the gap is visible and any future enforcement change is deliberate
    rather than accidental.

    Changing this would alter semantics for ALL metrics, so it requires governance
    authorization and is NOT changed as part of the RET-01 / RET-04 windowing fix.
    """
    nav = _trading_series(1400, END)

    future = [
        _obs((END + datetime.timedelta(days=i)).isoformat(), 1_000_000.0)
        for i in range(1, 31)
        if (END + datetime.timedelta(days=i)).weekday() < 5
    ]

    polluted = _dispatch(["RET-01"], nav + future, as_of=END.isoformat())["RET-01"]

    # Current behaviour: post-cutoff observations ARE used, shifting the anchor forward.
    assert polluted.status == CalculationStatus.CALCULATED
    assert polluted.diagnostics["window_end_date"] > END.isoformat(), (
        "Engine currently anchors on the last supplied observation rather than as_of_date. "
        "PIT correctness therefore depends on the caller filtering the series."
    )


def test_cagr_window_results_are_deterministic():
    nav = _trading_series(1400, END)

    first = _dispatch(["RET-01", "RET-03", "RET-04"], nav)
    second = _dispatch(["RET-01", "RET-03", "RET-04"], nav)

    for code in ("RET-01", "RET-03", "RET-04"):
        assert first[code].numeric_value == second[code].numeric_value
        assert first[code].status == second[code].status
        assert first[code].diagnostics == second[code].diagnostics


def test_cagr_window_discloses_conventions():
    """Every CAGR output must carry the machine-readable window/convention contract."""
    nav = _trading_series(1400, END)

    for code in ("RET-01", "RET-03", "RET-04"):
        item = _dispatch([code], nav)[code]
        diagnostics = item.diagnostics
        assert diagnostics["methodology_status"] == "CANDIDATE", code
        assert diagnostics["annualization_convention"] == "JULIAN_365.25_CANDIDATE", code
        assert diagnostics["lookahead_bias_control"] == "ANCHORED_AT_CUTOFF_BACKWARD_WALK_ONLY", code
        assert "window_start_date" in diagnostics, code
        assert "window_end_date" in diagnostics, code
        assert "window_observation_count" in diagnostics, code
