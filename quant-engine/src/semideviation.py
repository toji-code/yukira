"""Semi-deviation calculations."""

import math
from collections.abc import Sequence


def downside_semideviation(
    returns: Sequence[float],
    target_return: float = 0.0,
    periods_per_year: int | float | None = None,
) -> float:
    """
    Calculate downside semi-deviation.

    Only observations below the target return contribute to the measure.

    Formula:

        downside deviation =
            sqrt(
                sum(min(R_i - target, 0)^2)
                / (n - 1)
            )

    If periods_per_year is supplied, the result is annualized by
    multiplying by sqrt(periods_per_year).

    This function uses the full observation count in the denominator,
    consistent with the downside-deviation convention already used by
    the quant engine.
    """
    if len(returns) == 0:
        raise ValueError("returns cannot be empty")

    if not isinstance(target_return, (int, float)):
        raise TypeError("target_return must be numeric")

    if not math.isfinite(target_return):
        raise ValueError("target_return must be finite")

    if periods_per_year is not None:
        if not isinstance(periods_per_year, (int, float)):
            raise TypeError("periods_per_year must be numeric")

        if not math.isfinite(periods_per_year) or periods_per_year <= 0:
            raise ValueError(
                "periods_per_year must be finite and greater than zero"
            )

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in returns
    ):
        raise ValueError("returns must be finite numeric values")

    downside_squared = sum(
        min(value - target_return, 0.0) ** 2
        for value in returns
    )

    if len(returns) < 2:
        raise ValueError("at least two observations are required")

    result = math.sqrt(
        downside_squared / (len(returns) - 1)
    )

    if periods_per_year is not None:
        result *= math.sqrt(periods_per_year)

    return float(result)