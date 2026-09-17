import pytest

from src.turnover import weight_turnover


def test_no_weight_change():
    previous = [0.50, 0.30, 0.20]
    current = [0.50, 0.30, 0.20]

    result = weight_turnover(previous, current)

    assert result == pytest.approx(0.0)


def test_weight_shift_between_two_holdings():
    previous = [0.60, 0.40]
    current = [0.50, 0.50]

    result = weight_turnover(previous, current)

    assert result == pytest.approx(0.10)


def test_complete_reallocation():
    previous = [1.0, 0.0]
    current = [0.0, 1.0]

    result = weight_turnover(previous, current)

    assert result == pytest.approx(1.0)


def test_multiple_weight_changes():
    previous = [0.50, 0.30, 0.20]
    current = [0.40, 0.40, 0.20]

    result = weight_turnover(previous, current)

    assert result == pytest.approx(0.10)


def test_equal_weights_rebalanced():
    previous = [0.25, 0.25, 0.25, 0.25]
    current = [0.40, 0.20, 0.20, 0.20]

    result = weight_turnover(previous, current)

    assert result == pytest.approx(0.15)


def test_weight_series_must_have_equal_length():
    with pytest.raises(ValueError):
        weight_turnover([0.60, 0.40], [0.50, 0.30, 0.20])


def test_empty_weights_rejected():
    with pytest.raises(ValueError):
        weight_turnover([], [])


def test_negative_previous_weight_rejected():
    with pytest.raises(ValueError):
        weight_turnover([-0.10, 1.10], [0.50, 0.50])


def test_negative_current_weight_rejected():
    with pytest.raises(ValueError):
        weight_turnover([0.50, 0.50], [-0.10, 1.10])


def test_previous_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        weight_turnover([0.50, 0.40], [0.50, 0.50])


def test_current_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        weight_turnover([0.50, 0.50], [0.60, 0.30])


def test_non_finite_previous_weight_rejected():
    with pytest.raises(ValueError):
        weight_turnover([float("nan"), 0.50, 0.50], [0.40, 0.30, 0.30])


def test_non_finite_current_weight_rejected():
    with pytest.raises(ValueError):
        weight_turnover([0.50, 0.30, 0.20], [0.40, float("inf"), 0.60])