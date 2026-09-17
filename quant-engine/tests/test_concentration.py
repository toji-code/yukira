import pytest

from src.concentration import (
    effective_number_of_holdings,
    herfindahl_index,
)


def test_single_holding():
    result = herfindahl_index([1.0])

    assert result == pytest.approx(1.0)


def test_two_equal_holdings():
    result = herfindahl_index([0.50, 0.50])

    assert result == pytest.approx(0.50)


def test_four_equal_holdings():
    result = herfindahl_index([0.25, 0.25, 0.25, 0.25])

    assert result == pytest.approx(0.25)


def test_concentrated_portfolio():
    result = herfindahl_index([0.70, 0.20, 0.10])

    assert result == pytest.approx(0.54)


def test_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        herfindahl_index([0.50, 0.40])


def test_negative_weights_rejected():
    with pytest.raises(ValueError):
        herfindahl_index([1.10, -0.10])


def test_empty_weights_rejected():
    with pytest.raises(ValueError):
        herfindahl_index([])


def test_non_finite_weights_rejected():
    with pytest.raises(ValueError):
        herfindahl_index([0.50, float("nan"), 0.50])


def test_effective_number_single_holding():
    result = effective_number_of_holdings([1.0])

    assert result == pytest.approx(1.0)


def test_effective_number_equal_holdings():
    result = effective_number_of_holdings([0.25, 0.25, 0.25, 0.25])

    assert result == pytest.approx(4.0)


def test_effective_number_concentrated_portfolio():
    result = effective_number_of_holdings([0.70, 0.20, 0.10])

    assert result == pytest.approx(1.0 / 0.54)