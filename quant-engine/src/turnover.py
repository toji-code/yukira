"""Portfolio turnover and weight-change calculations."""

import math
from collections.abc import Sequence


def weight_turnover(
    previous_weights: Sequence[float],
    current_weights: Sequence[float],
) -> float:
    """
    Calculate portfolio weight turnover between two observations.

    Formula:
        weight_turnover = 0.5 * sum(abs(current_weight - previous_weight))

    Both portfolios must contain the same number of holdings, with weights
    expressed as fractions and summing to 1.

    The result represents the fraction of portfolio weight that changed
    hands between the two snapshots.
    """
    if len(previous_weights) != len(current_weights):
        raise ValueError("weight series must have equal length")

    if len(previous_weights) == 0:
        raise ValueError("weight series cannot be empty")

    if any(
        not isinstance(weight, (int, float)) or not math.isfinite(weight)
        for weight in previous_weights
    ):
        raise ValueError("previous weights must be finite numeric values")

    if any(
        not isinstance(weight, (int, float)) or not math.isfinite(weight)
        for weight in current_weights
    ):
        raise ValueError("current weights must be finite numeric values")

    if any(weight < 0 for weight in previous_weights):
        raise ValueError("previous weights cannot be negative")

    if any(weight < 0 for weight in current_weights):
        raise ValueError("current weights cannot be negative")

    if not math.isclose(
        sum(previous_weights),
        1.0,
        rel_tol=0.0,
        abs_tol=1e-9,
    ):
        raise ValueError("previous weights must sum to 1")

    if not math.isclose(
        sum(current_weights),
        1.0,
        rel_tol=0.0,
        abs_tol=1e-9,
    ):
        raise ValueError("current weights must sum to 1")

    return float(
        0.5
        * sum(
            abs(current - previous)
            for previous, current in zip(previous_weights, current_weights)
        )
    )