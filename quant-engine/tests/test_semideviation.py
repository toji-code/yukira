import math

import pytest

from src.semideviation import downside_semideviation


def test_downside_semideviation():
    returns = [-0.10, 0.05, -0.05, 0.02]

    expected = math.sqrt(
        ((-0.10) ** 2 + (-0.05) ** 2) / 3
    )

    result = downside_semideviation(returns)

    assert result == pytest.approx(expected)


def test_positive_returns_contribute_zero():
    returns = [0.05, 0.10, 0.02, 0.03]

    result = downside_semideviation(returns)

    assert result == pytest.approx(0.0)


def test_all_negative_returns():
    returns = [-0.05, -0.10, -0.15]

    expected = math.sqrt(
        ((-0.05) ** 2 + (-0.10) ** 2 + (-0.15) ** 2) / 2
    )

    result = downside_semideviation(returns)

    assert result == pytest.approx(expected)


def test_custom_target_return():
    returns = [0.02, 0.05, 0.08, 0.10]
    target = 0.06

    expected = math.sqrt(
        ((0.02 - 0.06) ** 2 + (0.05 - 0.06) ** 2) / 3
    )

    result = downside_semideviation(
        returns,
        target_return=target,
    )

    assert result == pytest.approx(expected)


def test_annualization():
    returns = [-0.10, 0.05, -0.05, 0.02]

    unannualized = downside_semideviation(returns)

    annualized = downside_semideviation(
        returns,
        periods_per_year=4,
    )

    assert annualized == pytest.approx(unannualized * 2)


def test_different_annualization_factors():
    returns = [-0.10, 0.05, -0.05, 0.02]

    quarterly = downside_semideviation(
        returns,
        periods_per_year=4,
    )

    monthly = downside_semideviation(
        returns,
        periods_per_year=12,
    )

    assert monthly == pytest.approx(
        quarterly * math.sqrt(3)
    )


def test_empty_returns_rejected():
    with pytest.raises(ValueError):
        downside_semideviation([])


def test_single_observation_rejected():
    with pytest.raises(ValueError):
        downside_semideviation([-0.05])


def test_non_finite_return_rejected():
    with pytest.raises(ValueError):
        downside_semideviation([0.05, float("nan")])


def test_non_numeric_target_rejected():
    with pytest.raises(TypeError):
        downside_semideviation(
            [-0.05, 0.02],
            target_return="0.0",
        )


def test_non_finite_target_rejected():
    with pytest.raises(ValueError):
        downside_semideviation(
            [-0.05, 0.02],
            target_return=float("inf"),
        )


def test_invalid_periods_per_year_rejected():
    with pytest.raises(ValueError):
        downside_semideviation(
            [-0.05, 0.02],
            periods_per_year=0,
        )


def test_non_numeric_periods_per_year_rejected():
    with pytest.raises(TypeError):
        downside_semideviation(
            [-0.05, 0.02],
            periods_per_year="12",
        )