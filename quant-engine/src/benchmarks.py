"""Benchmark-relative return calculations."""


def relative_return(
    portfolio_return: float,
    benchmark_return: float,
) -> float:
    """Calculate portfolio return minus benchmark return."""
    if not isinstance(portfolio_return, (int, float)):
        raise TypeError("portfolio_return must be numeric")

    if not isinstance(benchmark_return, (int, float)):
        raise TypeError("benchmark_return must be numeric")

    return float(portfolio_return - benchmark_return)