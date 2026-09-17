import pytest

from src.downside_beta import downside_beta


def test_downside_beta_equals_two():
    portfolio = [0.05, -0.02, 0.04, -0.06]
    benchmark = [0.03, -0.01, 0.02, -0.03]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_downside_beta_equals_one():
    portfolio = [0.05, -0.01, 0.04, -0.03]
    benchmark = [0.03, -0.01, 0.02, -0.03]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_upside_periods_are_excluded():
    portfolio = [0.20, -0.02, 0.30, -0.04]
    benchmark = [0.10, -0.01, 0.20, -0.02]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_negative_downside_beta():
    portfolio = [0.05, -0.01, 0.02, -0.03]
    benchmark = [0.10, -0.02, 0.04, -0.06]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(0.5)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        downside_beta([0.01, -0.02], [-0.01])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        downside_beta([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [float("nan"), -0.02, -0.03],
            [-0.01, -0.02, -0.03],
        )


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [-0.01, -0.02, -0.03],
            [float("nan"), -0.02, -0.03],
        )


def test_insufficient_downside_observations_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [0.05, -0.02, 0.03],
            [0.04, -0.01, 0.02],
        )


def test_no_downside_observations_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [0.05, 0.02, 0.03],
            [0.04, 0.01, 0.02],
        )


def test_zero_downside_benchmark_variance_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [-0.01, -0.02, -0.03],
            [-0.02, -0.02, -0.02],
        )