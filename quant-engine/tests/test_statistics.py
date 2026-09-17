import pytest

from src.statistics import (
    downside_deviation,
    periodic_returns,
    volatility,
)


def test_periodic_returns():
    values = [100.0, 110.0, 99.0]

    result = periodic_returns(values)

    assert result == pytest.approx([0.10, -0.10])


def test_periodic_returns_requires_two_observations():
    with pytest.raises(ValueError):
        periodic_returns([100.0])


def test_periodic_returns_rejects_zero():
    with pytest.raises(ValueError):
        periodic_returns([100.0, 0.0])


def test_periodic_returns_rejects_negative_values():
    with pytest.raises(ValueError):
        periodic_returns([100.0, -10.0])


def test_volatility():
    returns = [0.10, -0.10]

    result = volatility(returns, periods_per_year=1.0)

    assert result == pytest.approx(
        0.1414213562
    )


def test_volatility_annualization():
    returns = [0.10, -0.10]

    result = volatility(returns, periods_per_year=4.0)

    assert result == pytest.approx(
        0.2828427125
    )


def test_volatility_requires_two_observations():
    with pytest.raises(ValueError):
        volatility([0.10], periods_per_year=252.0)


def test_volatility_rejects_invalid_periods():
    with pytest.raises(ValueError):
        volatility([0.10, 0.20], periods_per_year=0.0)


def test_downside_deviation():
    returns = [0.10, -0.10]

    result = downside_deviation(
        returns,
        target_return=0.0,
        periods_per_year=1.0,
    )

    assert result == pytest.approx(
        0.0707106781
    )


def test_downside_deviation_ignores_upside():
    returns = [0.10, 0.20, 0.30]

    result = downside_deviation(
        returns,
        target_return=0.0,
        periods_per_year=1.0,
    )

    assert result == pytest.approx(0.0)


def test_downside_deviation_requires_valid_periods():
    with pytest.raises(ValueError):
        downside_deviation(
            [0.10, -0.10],
            target_return=0.0,
            periods_per_year=0.0,
        )