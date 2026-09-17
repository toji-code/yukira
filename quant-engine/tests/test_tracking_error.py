import math

import pytest

from src.tracking_error import tracking_error


def test_tracking_error():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    active = [0.02, 0.02, 0.03, 0.01]
    mean_active = sum(active) / len(active)

    expected = math.sqrt(
        sum((value - mean_active) ** 2 for value in active)
        / (len(active) - 1)
    )

    result = tracking_error(portfolio, benchmark)

    assert result == pytest.approx(expected)


def test_zero_tracking_error():
    portfolio = [0.02, 0.04, 0.03]
    benchmark = [0.02, 0.04, 0.03]

    result = tracking_error(portfolio, benchmark)

    assert result == pytest.approx(0.0)


def test_tracking_error_is_non_negative():
    portfolio = [0.05, 0.01, 0.07, 0.03]
    benchmark = [0.02, 0.04, 0.05, 0.01]

    result = tracking_error(portfolio, benchmark)

    assert result >= 0.0


def test_annualization():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    unannualized = tracking_error(
        portfolio,
        benchmark,
    )

    annualized = tracking_error(
        portfolio,
        benchmark,
        periods_per_year=4,
    )

    assert annualized == pytest.approx(unannualized * 2)


def test_different_annualization_factors():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    quarterly = tracking_error(
        portfolio,
        benchmark,
        periods_per_year=4,
    )

    monthly = tracking_error(
        portfolio,
        benchmark,
        periods_per_year=12,
    )

    assert monthly == pytest.approx(
        quarterly * math.sqrt(3)
    )


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        tracking_error(
            [0.04, 0.06],
            [0.02],
        )


def test_insufficient_observations_rejected():
    with pytest.raises(ValueError):
        tracking_error(
            [0.04],
            [0.02],
        )


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        tracking_error([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        tracking_error(
            [0.04, float("nan")],
            [0.02, 0.03],
        )


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        tracking_error(
            [0.04, 0.06],
            [0.02, float("inf")],
        )


def test_invalid_periods_per_year_rejected():
    with pytest.raises(ValueError):
        tracking_error(
            [0.04, 0.06],
            [0.02, 0.03],
            periods_per_year=0,
        )


def test_non_numeric_periods_per_year_rejected():
    with pytest.raises(TypeError):
        tracking_error(
            [0.04, 0.06],
            [0.02, 0.03],
            periods_per_year="12",
        )