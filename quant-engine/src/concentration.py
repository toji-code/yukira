"""Portfolio concentration calculations."""

import math
from collections.abc import Sequence


def herfindahl_index(weights: Sequence[float]) -> float:
    """
    Calculate the Herfindahl-Hirschman Index (HHI).

    Formula:
        HHI = sum(weight_i ** 2)

    Weights must be non-negative and sum to 1.
    The result ranges from approximately 1/n for an equally
    weighted portfolio of n holdings to 1.0 for a single holding.
    """
    if len(weights) == 0:
        raise ValueError("weights cannot be empty")

    if any(
        not isinstance(weight, (int, float)) or not math.isfinite(weight)
        for weight in weights
    ):
        raise ValueError("weights must be finite numeric values")

    if any(weight < 0 for weight in weights):
        raise ValueError("weights cannot be negative")

    if not math.isclose(sum(weights), 1.0, rel_tol=0.0, abs_tol=1e-9):
        raise ValueError("weights must sum to 1")

    return float(sum(weight**2 for weight in weights))


def effective_number_of_holdings(weights: Sequence[float]) -> float:
    """
    Calculate the effective number of holdings from HHI.

    Formula:
        ENH = 1 / HHI

    A portfolio with equal weights across n holdings has ENH = n.
    """
    hhi = herfindahl_index(weights)

    return float(1.0 / hhi)