"""Treynor Ratio calculations."""

import math


def treynor_ratio(
    portfolio_return: float,
    risk_free_rate: float,
    portfolio_beta: float,
) -> float:
    """
    Calculate the Treynor Ratio.

    Formula:

        Treynor Ratio = (portfolio_return - risk_free_rate)
                        / portfolio_beta

    All return inputs must use the same period and representation.

    The ratio measures excess return earned per unit of systematic risk.
    """
    values = {
        "portfolio_return": portfolio_return,
        "risk_free_rate": risk_free_rate,
        "portfolio_beta": portfolio_beta,
    }

    for name, value in values.items():
        if not isinstance(value, (int, float)):
            raise TypeError(f"{name} must be numeric")

        if not math.isfinite(value):
            raise ValueError(f"{name} must be finite")

    if math.isclose(portfolio_beta, 0.0, abs_tol=1e-15):
        raise ValueError("portfolio beta must be non-zero")

    return float(
        (portfolio_return - risk_free_rate)
        / portfolio_beta
    )