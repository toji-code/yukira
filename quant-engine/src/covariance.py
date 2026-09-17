"""Covariance calculations."""

import math
from collections.abc import Sequence


def sample_covariance(
    first_values: Sequence[float],
    second_values: Sequence[float],
) -> float:
    """
    Calculate sample covariance between two numeric series.

    Formula:

        Cov(X, Y) =
            sum((x_i - mean_x) * (y_i - mean_y)) / (n - 1)

    Sample covariance is used because the observed return series is
    treated as a sample from the underlying return-generating process.
    """
    if len(first_values) != len(second_values):
        raise ValueError("value series must have equal length")

    if len(first_values) < 2:
        raise ValueError("at least two observations are required")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in first_values
    ):
        raise ValueError("first values must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in second_values
    ):
        raise ValueError("second values must be finite numeric values")

    first_mean = sum(first_values) / len(first_values)
    second_mean = sum(second_values) / len(second_values)

    covariance = sum(
        (first - first_mean) * (second - second_mean)
        for first, second in zip(first_values, second_values)
    ) / (len(first_values) - 1)

    return float(covariance)