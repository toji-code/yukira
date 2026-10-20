"""Brinson-Fachler (1985) portfolio performance attribution kernel."""

import math
from collections.abc import Sequence
from dataclasses import dataclass


@dataclass(frozen=True)
class SegmentAttributionResult:
    """Attribution breakdown for an individual portfolio segment."""

    segment_name: str
    portfolio_weight: float
    benchmark_weight: float
    portfolio_return: float
    benchmark_return: float
    allocation_effect: float
    selection_effect: float
    interaction_effect: float
    total_segment_effect: float


@dataclass(frozen=True)
class BrinsonFachlerAttributionSummary:
    """Summary of Brinson-Fachler attribution across all segments."""

    portfolio_return: float
    benchmark_return: float
    active_return: float
    total_allocation_effect: float
    total_selection_effect: float
    total_interaction_effect: float
    coverage_residual: float
    reconciled_total: float
    reconciliation_difference: float
    is_reconciled: bool = True
    segments: Sequence[SegmentAttributionResult] = ()


def brinson_fachler_attribution(
    segment_names: Sequence[str],
    portfolio_weights: Sequence[float],
    benchmark_weights: Sequence[float],
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    total_portfolio_return: float | None = None,
    total_benchmark_return: float | None = None,
    tolerance: float = 1e-6,
) -> BrinsonFachlerAttributionSummary:
    """
    Calculate Brinson-Fachler (1985) active return attribution.

    Mathematical Definition:
        Allocation effect (A_i):
            A_i = (w_{p, i} - w_{b, i}) * (R_{b, i} - R_b)
        Selection effect (S_i):
            S_i = w_{b, i} * (R_{p, i} - R_{b, i})
        Interaction effect (I_i):
            I_i = (w_{p, i} - w_{b, i}) * (R_{p, i} - R_{b, i})

    Reconciliation:
        Active Return (R_A) = R_p - R_b
        Sum(A_i) + Sum(S_i) + Sum(I_i) + Coverage_Residual = R_A
    """
    n = len(segment_names)
    if not (len(portfolio_weights) == n == len(benchmark_weights) == len(portfolio_returns) == len(benchmark_returns)):
        raise ValueError("all segment attribute sequences must have identical length")

    if n == 0:
        raise ValueError("at least one segment is required for attribution")

    for seq_name, seq in [
        ("portfolio_weights", portfolio_weights),
        ("benchmark_weights", benchmark_weights),
        ("portfolio_returns", portfolio_returns),
        ("benchmark_returns", benchmark_returns),
    ]:
        if any(not isinstance(v, (int, float)) or not math.isfinite(v) for v in seq):
            raise ValueError(f"{seq_name} must contain finite numeric values")

    # Calculate overall returns if not explicitly provided
    r_p = float(total_portfolio_return if total_portfolio_return is not None
                else sum(w * r for w, r in zip(portfolio_weights, portfolio_returns)))
    r_b = float(total_benchmark_return if total_benchmark_return is not None
                else sum(w * r for w, r in zip(benchmark_weights, benchmark_returns)))
    r_a = r_p - r_b

    segment_results: list[SegmentAttributionResult] = []
    tot_alloc = 0.0
    tot_select = 0.0
    tot_inter = 0.0

    sum_wp = sum(portfolio_weights)
    sum_wb = sum(benchmark_weights)
    weight_diff = sum_wp - sum_wb
    # Coverage residual if weights do not perfectly cancel
    coverage_res = -r_b * weight_diff if abs(weight_diff) > 1e-9 else 0.0

    for name, wp, wb, rp, rb in zip(
        segment_names, portfolio_weights, benchmark_weights, portfolio_returns, benchmark_returns
    ):
        alloc = (wp - wb) * (rb - r_b)
        select = wb * (rp - rb)
        inter = (wp - wb) * (rp - rb)
        tot_seg = alloc + select + inter

        tot_alloc += alloc
        tot_select += select
        tot_inter += inter

        segment_results.append(
            SegmentAttributionResult(
                segment_name=str(name),
                portfolio_weight=float(wp),
                benchmark_weight=float(wb),
                portfolio_return=float(rp),
                benchmark_return=float(rb),
                allocation_effect=float(alloc),
                selection_effect=float(select),
                interaction_effect=float(inter),
                total_segment_effect=float(tot_seg),
            )
        )

    reconciled = tot_alloc + tot_select + tot_inter + coverage_res
    diff = abs(reconciled - r_a)
    is_rec = diff <= tolerance

    return BrinsonFachlerAttributionSummary(
        portfolio_return=r_p,
        benchmark_return=r_b,
        active_return=r_a,
        total_allocation_effect=float(tot_alloc),
        total_selection_effect=float(tot_select),
        total_interaction_effect=float(tot_inter),
        coverage_residual=float(coverage_res),
        reconciled_total=float(reconciled),
        reconciliation_difference=float(diff),
        is_reconciled=bool(is_rec),
        segments=tuple(segment_results),
    )
