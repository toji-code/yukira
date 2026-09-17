import pytest

from src.active_share import active_share


def test_identical_portfolio_and_benchmark():
    portfolio = [0.50, 0.30, 0.20]
    benchmark = [0.50, 0.30, 0.20]

    result = active_share(portfolio, benchmark)

    assert result == pytest.approx(0.0)


def test_active_share_with_different_weights():
    portfolio = [0.60, 0.40]
    benchmark = [0.50, 0.50]

    result = active_share(portfolio, benchmark)

    assert result == pytest.approx(0.10)


def test_completely_different_portfolios():
    portfolio = [1.0, 0.0]
    benchmark = [0.0, 1.0]

    result = active_share(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_multiple_holdings():
    portfolio = [0.40, 0.35, 0.25]
    benchmark = [0.30, 0.30, 0.40]

    result = active_share(portfolio, benchmark)

    assert result == pytest.approx(0.15)


def test_active_share_is_symmetric():
    portfolio = [0.60, 0.30, 0.10]
    benchmark = [0.40, 0.40, 0.20]

    assert active_share(portfolio, benchmark) == pytest.approx(
        active_share(benchmark, portfolio)
    )


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        active_share([0.60, 0.40], [0.50, 0.30, 0.20])


def test_empty_weights_rejected():
    with pytest.raises(ValueError):
        active_share([], [])


def test_negative_portfolio_weight_rejected():
    with pytest.raises(ValueError):
        active_share([-0.10, 1.10], [0.50, 0.50])


def test_negative_benchmark_weight_rejected():
    with pytest.raises(ValueError):
        active_share([0.50, 0.50], [-0.10, 1.10])


def test_portfolio_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        active_share([0.50, 0.40], [0.50, 0.50])


def test_benchmark_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        active_share([0.50, 0.50], [0.60, 0.30])


def test_non_finite_portfolio_weight_rejected():
    with pytest.raises(ValueError):
        active_share(
            [float("nan"), 0.50, 0.50],
            [0.40, 0.30, 0.30],
        )


def test_non_finite_benchmark_weight_rejected():
    with pytest.raises(ValueError):
        active_share(
            [0.40, 0.30, 0.30],
            [0.40, float("inf"), 0.60],
        )