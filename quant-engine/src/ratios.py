from __future__ import annotations

import math
from statistics import mean, stdev
from typing import Sequence

from src.statistics import downside_deviation


def _validate_returns(returns: Sequence[float]) -> None:
    if len(returns) < 2:
        raise ValueError("At least two return observations are required.")

    for value in returns:
        if not math.isfinite(value):
            raise ValueError("returns must contain only finite numbers.")


def sharpe_ratio(
    returns: Sequence[float],
    risk_free_rate: float | Sequence[float],
    periods_per_year: float = 252.0,
) -> float:
    """
    Calculate the annualized Sharpe ratio.

    The risk-free rate is expected to be expressed as a
    return for the same period as each observation, either as a constant
    scalar or as a synchronous sequence of daily risk-free rates.

    Formula:

        mean(period_return - risk_free_rate)
        ---------------------------------
              sample standard deviation

        multiplied by sqrt(periods_per_year).

    A zero standard deviation is rejected because the Sharpe
    ratio would be undefined.
    """
    _validate_returns(returns)

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError(
            "periods_per_year must be finite and greater than zero."
        )

    if isinstance(risk_free_rate, (int, float)):
        if not math.isfinite(risk_free_rate):
            raise ValueError("risk_free_rate must be finite.")
        excess_returns = [
            return_value - float(risk_free_rate)
            for return_value in returns
        ]
    elif isinstance(risk_free_rate, Sequence):
        if len(risk_free_rate) != len(returns):
            raise ValueError(
                "risk_free_rate sequence must have the same length as returns."
            )
        for rf in risk_free_rate:
            if not isinstance(rf, (int, float)) or not math.isfinite(rf):
                raise ValueError("risk_free_rate sequence must contain only finite numbers.")
        excess_returns = [
            return_value - float(rf)
            for return_value, rf in zip(returns, risk_free_rate)
        ]
    else:
        raise TypeError("risk_free_rate must be a float or a Sequence of floats.")

    standard_deviation = stdev(excess_returns)

    if standard_deviation == 0:
        raise ValueError(
            "Sharpe ratio is undefined when excess-return volatility is zero."
        )

    return (
        mean(excess_returns)
        / standard_deviation
        * math.sqrt(periods_per_year)
    )


def sortino_ratio(
    returns: Sequence[float],
    target_return: float,
    periods_per_year: float,
) -> float:
    """
    Calculate an annualized Sortino ratio.

    The target return is expressed per observation period.

    Numerator:
        annualized arithmetic excess return

    Denominator:
        annualized downside deviation

    Formula:

        mean(returns - target_return) * periods_per_year
        -------------------------------------------------
                 annualized downside deviation

    A zero downside deviation is rejected because the ratio
    would be undefined.
    """
    _validate_returns(returns)

    if not math.isfinite(target_return):
        raise ValueError("target_return must be finite.")

    if not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError(
            "periods_per_year must be finite and greater than zero."
        )

    annualized_excess_return = (
        mean(
            return_value - target_return
            for return_value in returns
        )
        * periods_per_year
    )

    annualized_downside = downside_deviation(
        returns,
        target_return,
        periods_per_year,
    )

    if annualized_downside == 0:
        raise ValueError(
            "Sortino ratio is undefined when downside deviation is zero."
        )

    return annualized_excess_return / annualized_downside