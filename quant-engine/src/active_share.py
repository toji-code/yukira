"""Active Share calculations."""

import math
from collections.abc import Sequence


def active_share(
    portfolio_weights: Sequence[float],
    benchmark_weights: Sequence[float],
) -> float:
    """
    Calculate Active Share between a portfolio and its benchmark.

    Formula:
        Active Share = 0.5 * sum(abs(portfolio_weight - benchmark_weight))

    Both weight vectors must contain the same holdings in the same order,
    with weights expressed as fractions and each vector summing to 1.

    Result:
        0.0 = identical portfolio and benchmark
        1.0 = completely different portfolios
    """
    if len(portfolio_weights) != len(benchmark_weights):
        raise ValueError("portfolio and benchmark weights must have equal length")

    if len(portfolio_weights) == 0:
        raise ValueError("weight series cannot be empty")

    if any(
        not isinstance(weight, (int, float)) or not math.isfinite(weight)
        for weight in portfolio_weights
    ):
        raise ValueError("portfolio weights must be finite numeric values")

    if any(
        not isinstance(weight, (int, float)) or not math.isfinite(weight)
        for weight in benchmark_weights
    ):
        raise ValueError("benchmark weights must be finite numeric values")

    if any(weight < 0 for weight in portfolio_weights):
        raise ValueError("portfolio weights cannot be negative")

    if any(weight < 0 for weight in benchmark_weights):
        raise ValueError("benchmark weights cannot be negative")

    if not math.isclose(
        sum(portfolio_weights),
        1.0,
        rel_tol=0.0,
        abs_tol=1e-9,
    ):
        raise ValueError("portfolio weights must sum to 1")

    if not math.isclose(
        sum(benchmark_weights),
        1.0,
        rel_tol=0.0,
        abs_tol=1e-9,
    ):
        raise ValueError("benchmark weights must sum to 1")

    return float(
        0.5
        * sum(
            abs(portfolio - benchmark)
            for portfolio, benchmark in zip(
                portfolio_weights,
                benchmark_weights,
            )
        )
    )