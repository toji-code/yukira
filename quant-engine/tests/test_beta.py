import pytest

from src.beta import beta


def test_beta_equals_one_for_identical_returns():
    portfolio = [0.01, 0.02, -0.01, 0.03]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_beta_equals_two_for_double_benchmark_movements():
    portfolio = [0.02, 0.04, -0.02, 0.06]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_beta_equals_zero_for_uncorrelated_constant_portfolio():
    portfolio = [0.02, 0.02, 0.02, 0.02]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(0.0)


def test_negative_beta():
    portfolio = [-0.01, -0.02, 0.01, -0.03]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(-1.0)


def test_beta_with_shifted_returns():
    benchmark = [0.01, 0.02, -0.01, 0.03]
    portfolio = [0.06, 0.07, 0.04, 0.08]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02], [0.01])


def test_insufficient_observations_rejected():
    with pytest.raises(ValueError):
        beta([0.01], [0.02])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        beta([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        beta([0.01, float("nan")], [0.01, 0.02])


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02], [0.01, float("inf")])


def test_zero_benchmark_variance_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02, 0.03], [0.02, 0.02, 0.02])