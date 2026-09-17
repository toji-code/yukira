"""Return correlation calculations."""

import math
from collections.abc import Sequence


def pearson_correlation(
    first_returns: Sequence[float],
    second_returns: Sequence[float],
) -> float:
    """
    Calculate Pearson correlation between two return series.

    Formula:

        correlation =
            covariance(X, Y)
            / (std(X) * std(Y))

    Sample covariance and sample standard deviations are used.

    The result is bounded between -1 and 1:
        +1 = perfect positive linear relationship
         0 = no linear relationship
        -1 = perfect negative linear relationship
    """
    if len(first_returns) != len(second_returns):
        raise ValueError("return series must have equal length")

    if len(first_returns) < 2:
        raise ValueError("at least two observations are required")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in first_returns
    ):
        raise ValueError("first returns must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in second_returns
    ):
        raise ValueError("second returns must be finite numeric values")

    first_mean = sum(first_returns) / len(first_returns)
    second_mean = sum(second_returns) / len(second_returns)

    first_deviations = [
        value - first_mean
        for value in first_returns
    ]

    second_deviations = [
        value - second_mean
        for value in second_returns
    ]

    sum_squared_first = sum(
        deviation**2
        for deviation in first_deviations
    )

    sum_squared_second = sum(
        deviation**2
        for deviation in second_deviations
    )

    if math.isclose(sum_squared_first, 0.0, abs_tol=1e-15):
        raise ValueError("first series must have non-zero variance")

    if math.isclose(sum_squared_second, 0.0, abs_tol=1e-15):
        raise ValueError("second series must have non-zero variance")

    covariance_numerator = sum(
        first_deviation * second_deviation
        for first_deviation, second_deviation in zip(
            first_deviations,
            second_deviations,
        )
    )

    denominator = math.sqrt(
        sum_squared_first * sum_squared_second
    )

    return float(covariance_numerator / denominator)