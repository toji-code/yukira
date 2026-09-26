"""Pre-specified historical regime engine and freeze gate for Phase 2S-A.

REGIME FREEZE PROTOCOL:
Before the first validation result is generated, all regime definitions, benchmark series,
date boundaries, and inclusion/exclusion rules are permanently frozen.
After execution begins, regime boundaries and rules CANNOT be modified to improve results.
Post-hoc regime selection and cherry-picking are strictly prohibited.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any, Sequence


class RegimeFreezeError(RuntimeError):
    """Raised when an attempt is made to modify regime definitions after freeze."""
    pass


@dataclass(frozen=True)
class FrozenRegime:
    """Immutable pre-specified macroeconomic regime definition."""
    identifier: str
    name: str
    start_date: str
    end_date: str
    benchmark_basis: str
    inclusion_rule: str
    min_observations: int = 126  # Minimum 6 months of trading observations required


# Authoritative, frozen regime definitions from locked Phase 2S scope
FROZEN_REGIMES: dict[str, FrozenRegime] = {
    "REG-01": FrozenRegime(
        identifier="REG-01",
        name="Liquidity Shock & Rapid Rebound",
        start_date="2020-02-01",
        end_date="2020-11-30",
        benchmark_basis="NIFTY 500 TRI peak-to-trough collapse exceeding 20% and subsequent recovery",
        inclusion_rule="Continuous trading days >= 126; requires valid daily NAV series across episode",
        min_observations=126,
    ),
    "REG-02": FrozenRegime(
        identifier="REG-02",
        name="Sustained Secular Bull Market",
        start_date="2020-12-01",
        end_date="2021-12-31",
        benchmark_basis="Benchmark annualized return exceeding +20% with maximum drawdown under 10%",
        inclusion_rule="Continuous trading days >= 126; verifies upside compounding and downside sample safety",
        min_observations=126,
    ),
    "REG-03": FrozenRegime(
        identifier="REG-03",
        name="Monetary Tightening & Sideways Consolidation",
        start_date="2022-04-01",
        end_date="2023-04-30",
        benchmark_basis="RBI off-cycle repo rate hike cycle (+40 bps) with range-bound benchmark trading",
        inclusion_rule="Continuous trading days >= 126; evaluates Sharpe under compressed excess returns",
        min_observations=126,
    ),
}

_REGIMES_LOCKED: bool = True


def get_frozen_regime(regime_id: str) -> FrozenRegime:
    """Retrieve pre-specified frozen regime by identifier."""
    if regime_id not in FROZEN_REGIMES:
        raise KeyError(
            f"Regime {regime_id} is not an authorized frozen regime. "
            f"Available frozen regimes: {list(FROZEN_REGIMES.keys())}"
        )
    return FROZEN_REGIMES[regime_id]


def assert_regimes_frozen() -> bool:
    """Verify that regime definitions are permanently locked against post-hoc alteration."""
    if not _REGIMES_LOCKED:
        raise RegimeFreezeError("Regime definitions are not locked!")
    return True


def slice_series_by_regime(
    observations: Sequence[dict[str, Any]],
    regime_id: str,
) -> tuple[list[dict[str, Any]], FrozenRegime]:
    """
    Slice an observation series by the frozen date boundaries of a pre-specified regime.
    Returns (sliced_observations, regime_spec).
    """
    assert_regimes_frozen()
    regime = get_frozen_regime(regime_id)
    start_d = date.fromisoformat(regime.start_date)
    end_d = date.fromisoformat(regime.end_date)

    sliced = []
    for obs in observations:
        obs_date_str = str(obs.get("date", obs.get("effective_date", ""))).split("T")[0]
        if not obs_date_str:
            continue
        obs_d = date.fromisoformat(obs_date_str)
        if start_d <= obs_d <= end_d:
            sliced.append(obs)

    return sorted(sliced, key=lambda x: str(x.get("date", x.get("effective_date", "")))), regime
