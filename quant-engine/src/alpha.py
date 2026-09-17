"""Jensen's Alpha calculations."""


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