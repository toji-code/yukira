import math
from collections.abc import Sequence

try:
    from src.beta import beta as calculate_beta
except ImportError:
    from .beta import beta as calculate_beta


def jensens_alpha(
    portfolio_return: float,
    benchmark_return: float,
    risk_free_rate: float,
    portfolio_beta: float,
) -> float:
    """
    Calculate Jensen's Alpha.

    Formula:
        alpha = portfolio_return
                - [risk_free_rate
                   + beta * (benchmark_return - risk_free_rate)]

    All rates must use the same period and representation
    (for example, all as decimal returns).

    Positive alpha means the portfolio return exceeded the
    CAPM-implied return for the supplied beta and benchmark.
    """
    values = {
        "portfolio_return": portfolio_return,
        "benchmark_return": benchmark_return,
        "risk_free_rate": risk_free_rate,
        "portfolio_beta": portfolio_beta,
    }

    for name, value in values.items():
        if not isinstance(value, (int, float)):
            raise TypeError(f"{name} must be numeric")

    expected_return = (
        risk_free_rate
        + portfolio_beta * (benchmark_return - risk_free_rate)
    )

    return float(portfolio_return - expected_return)


def jensens_alpha_ols(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    risk_free_rates: Sequence[float] | float | None = None,
    portfolio_beta: float | None = None,
    periods_per_year: float = 252.0,
) -> float:
    """
    Calculate annualized Jensen's Alpha using econometric time-series OLS regression intercept.

    Model:
        (Rp,t - Rf,t) = alpha_daily + beta * (Rb,t - Rf,t) + epsilon_t

    Closed-form estimators:
        yp,t = Rp,t - Rf,t (portfolio excess return)
        xb,t = Rb,t - Rf,t (benchmark excess return)
        beta = Cov(xb, yp) / Var(xb)  (reusing M2N-06)
        alpha_daily = mean(yp) - beta * mean(xb)
        alpha_annual = alpha_daily * periods_per_year (default 252)

    All return series must be synchronized daily simple returns.
    Risk-free rates must be daily accruals (M2N-02).
    """
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) < 2:
        raise ValueError("at least two observations are required")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in portfolio_returns
    ):
        raise ValueError("portfolio returns must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in benchmark_returns
    ):
        raise ValueError("benchmark returns must be finite numeric values")

    if not isinstance(periods_per_year, (int, float)) or not math.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year must be a positive finite numeric value")

    # Construct excess returns identically to M2N-06 in src.beta
    if risk_free_rates is None:
        y = list(portfolio_returns)
        x = list(benchmark_returns)
    elif isinstance(risk_free_rates, Sequence) and not isinstance(risk_free_rates, (str, bytes)):
        if len(risk_free_rates) != len(portfolio_returns):
            raise ValueError("portfolio returns and risk-free rates must have equal length")
        if any(
            not isinstance(value, (int, float)) or not math.isfinite(value)
            for value in risk_free_rates
        ):
            raise ValueError("risk-free rates must be finite numeric values")
        y = [p - rf for p, rf in zip(portfolio_returns, risk_free_rates)]
        x = [b - rf for b, rf in zip(benchmark_returns, risk_free_rates)]
    elif isinstance(risk_free_rates, (int, float)):
        if not math.isfinite(risk_free_rates):
            raise ValueError("risk-free rate must be a finite numeric value")
        y = [p - risk_free_rates for p in portfolio_returns]
        x = [b - risk_free_rates for b in benchmark_returns]
    else:
        raise TypeError("risk_free_rates must be None, numeric, or a Sequence of numeric values")

    # Calculate or validate beta using M2N-06
    if portfolio_beta is None:
        b_val = calculate_beta(portfolio_returns, benchmark_returns, risk_free_rates=risk_free_rates)
    else:
        if not isinstance(portfolio_beta, (int, float)) or not math.isfinite(portfolio_beta):
            raise TypeError("portfolio_beta must be a finite numeric value")
        # Ensure benchmark variance is valid even if beta was supplied
        n = len(x)
        x_mean = sum(x) / n
        benchmark_variance = sum((x_i - x_mean) ** 2 for x_i in x) / (n - 1)
        if math.isclose(benchmark_variance, 0.0, abs_tol=1e-15):
            raise ValueError("benchmark variance must be greater than zero")
        b_val = float(portfolio_beta)

    n = len(x)
    x_mean = sum(x) / n
    y_mean = sum(y) / n

    alpha_daily = y_mean - b_val * x_mean
    alpha_annual = alpha_daily * periods_per_year

    return float(alpha_annual)