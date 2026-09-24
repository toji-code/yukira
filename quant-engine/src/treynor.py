"""Treynor Ratio calculations."""

import math
from collections.abc import Sequence


def treynor_numerator(
    portfolio_returns: Sequence[float],
    risk_free_rates: Sequence[float] | float = 0.0,
    periods_per_year: float = 252.0,
) -> float:
    """
    Calculate the annualized portfolio excess return (Treynor numerator).

    Formula:
        mean(Rp,t - Rf,t) * periods_per_year

    Where:
        Rp,t is the daily portfolio simple return
        Rf,t is the daily risk-free return (from M2N-02 FBIL 91D T-Bill proxy or scalar)
    """
    if len(portfolio_returns) == 0:
        raise ValueError("portfolio returns cannot be empty")

    if any(
        not isinstance(val, (int, float)) or not math.isfinite(val)
        for val in portfolio_returns
    ):
        raise ValueError("portfolio returns must be finite numeric values")

    if not isinstance(periods_per_year, (int, float)) or not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year must be a positive finite numeric value")

    if isinstance(risk_free_rates, Sequence) and not isinstance(risk_free_rates, (str, bytes)):
        if len(risk_free_rates) != len(portfolio_returns):
            raise ValueError("portfolio returns and risk-free rates must have equal length")
        if any(
            not isinstance(val, (int, float)) or not math.isfinite(val)
            for val in risk_free_rates
        ):
            raise ValueError("risk-free rates must be finite numeric values")
        excess_returns = [p - rf for p, rf in zip(portfolio_returns, risk_free_rates)]
    elif isinstance(risk_free_rates, (int, float)):
        if not math.isfinite(risk_free_rates):
            raise ValueError("risk-free rate must be a finite numeric value")
        excess_returns = [p - risk_free_rates for p in portfolio_returns]
    else:
        raise TypeError("risk_free_rates must be a numeric value or Sequence of numeric values")

    mean_excess = sum(excess_returns) / len(excess_returns)
    return float(mean_excess * periods_per_year)


def treynor_ratio(
    portfolio_return: Sequence[float] | float,
    risk_free_rate: Sequence[float] | float,
    portfolio_beta: float,
    periods_per_year: float = 252.0,
) -> float:
    """
    Calculate the Treynor Ratio.

    Formula:
        Treynor Ratio = (Annualized Portfolio Excess Return) / portfolio_beta
                      = mean(Rp,t - Rf,t) * 252 / portfolio_beta

    If scalar inputs are provided (single-period):
        Treynor Ratio = (portfolio_return - risk_free_rate) / portfolio_beta
    """
    if not isinstance(portfolio_beta, (int, float)):
        raise TypeError("portfolio_beta must be numeric")

    if not math.isfinite(portfolio_beta):
        raise ValueError("portfolio_beta must be finite")

    if math.isclose(portfolio_beta, 0.0, abs_tol=1e-15):
        raise ValueError("portfolio beta must be non-zero")

    if isinstance(portfolio_return, Sequence) and not isinstance(portfolio_return, (str, bytes)):
        numerator = treynor_numerator(portfolio_return, risk_free_rate, periods_per_year)
    else:
        # Scalar single-period case
        if not isinstance(portfolio_return, (int, float)):
            raise TypeError("portfolio_return must be numeric")
        if not math.isfinite(portfolio_return):
            raise ValueError("portfolio_return must be finite")

        if not isinstance(risk_free_rate, (int, float)):
            raise TypeError("risk_free_rate must be numeric")
        if not math.isfinite(risk_free_rate):
            raise ValueError("risk_free_rate must be finite")

        numerator = float(portfolio_return - risk_free_rate)

    return float(numerator / portfolio_beta)