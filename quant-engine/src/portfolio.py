"""Portfolio-level deterministic calculations."""

import math
from collections.abc import Sequence


def weighted_portfolio_return(
    weights: Sequence[float],
    returns: Sequence[float],
) -> float:
    """
    Calculate the weighted return of a portfolio.

    Formula:
        portfolio_return = sum(weight_i * return_i)

    Weights must sum to 1 within a small numerical tolerance.
    """
    if len(weights) != len(returns):
        raise ValueError("weights and returns must have equal length")

    if len(weights) == 0:
        raise ValueError("weights and returns cannot be empty")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in weights
    ):
        raise ValueError("weights must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in returns
    ):
        raise ValueError("returns must be finite numeric values")

    if any(weight < 0 for weight in weights):
        raise ValueError("weights cannot be negative")

    if not math.isclose(sum(weights), 1.0, rel_tol=0.0, abs_tol=1e-9):
        raise ValueError("weights must sum to 1")

    return float(sum(weight * ret for weight, ret in zip(weights, returns)))