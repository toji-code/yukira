import math

import pytest

from src.information_ratio import information_ratio


def test_information_ratio_positive():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    result = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=1,
    )

    active = [0.02, 0.02, 0.03, 0.01]
    mean_active = sum(active) / len(active)

    variance = sum(
        (value - mean_active) ** 2
        for value in active
    ) / (len(active) - 1)

    expected = mean_active / math.sqrt(variance)

    assert result == pytest.approx(expected)


def test_information_ratio_negative():
    portfolio = [0.01, 0.02, 0.03, 0.01]
    benchmark = [0.03, 0.04, 0.04, 0.03]

    result = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=1,
    )

    assert result < 0


def test_information_ratio_zero_mean_active_return():
    portfolio = [0.02, 0.04, 0.03, 0.01]
    benchmark = [0.01, 0.03, 0.04, 0.02]

    result = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=1,
    )

    assert result == pytest.approx(0.0)


def test_annualization_is_explicit():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    one_period = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=1,
    )

    four_periods = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=4,
    )

    assert four_periods == pytest.approx(one_period * 2)


def test_different_periods_per_year():
    portfolio = [0.04, 0.06, 0.08, 0.05]
    benchmark = [0.02, 0.04, 0.05, 0.04]

    monthly = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=12,
    )

    quarterly = information_ratio(
        portfolio,
        benchmark,
        periods_per_year=4,
    )

    assert monthly == pytest.approx(
        quarterly * math.sqrt(3)
    )


def test_equal_length_requirement():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04, 0.06],
            [0.02],
            periods_per_year=12,
        )


def test_minimum_two_observations():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04],
            [0.02],
            periods_per_year=12,
        )


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04, float("nan")],
            [0.02, 0.03],
            periods_per_year=12,
        )


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04, 0.06],
            [0.02, float("inf")],
            periods_per_year=12,
        )


def test_invalid_periods_per_year_rejected():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04, 0.06],
            [0.02, 0.03],
            periods_per_year=0,
        )


def test_non_numeric_periods_per_year_rejected():
    with pytest.raises(TypeError):
        information_ratio(
            [0.04, 0.06],
            [0.02, 0.03],
            periods_per_year="12",
        )


def test_zero_tracking_error_rejected():
    with pytest.raises(ValueError):
        information_ratio(
            [0.04, 0.04, 0.04],
            [0.02, 0.02, 0.02],
            periods_per_year=12,
        )