import pytest

from src.rolling import rolling_returns


def test_rolling_returns():
    values = [100.0, 110.0, 121.0]

    result = rolling_returns(values, window=3)

    assert result == pytest.approx([0.21])


def test_multiple_rolling_windows():
    values = [100.0, 110.0, 99.0, 121.0]

    result = rolling_returns(values, window=2)

    assert result == pytest.approx(
        [0.10, -0.10, 121.0 / 99.0 - 1.0]
    )


def test_window_of_two():
    values = [100.0, 105.0, 110.0]

    result = rolling_returns(values, window=2)

    assert result == pytest.approx(
        [0.05, 110.0 / 105.0 - 1.0]
    )


def test_window_must_be_at_least_two():
    with pytest.raises(ValueError):
        rolling_returns([100.0, 110.0], window=1)


def test_window_cannot_exceed_data():
    with pytest.raises(ValueError):
        rolling_returns([100.0, 110.0], window=3)


def test_empty_values_rejected():
    with pytest.raises(ValueError):
        rolling_returns([], window=2)


def test_zero_values_rejected():
    with pytest.raises(ValueError):
        rolling_returns([100.0, 0.0, 110.0], window=2)


def test_negative_values_rejected():
    with pytest.raises(ValueError):
        rolling_returns([100.0, -10.0, 110.0], window=2)


def test_non_finite_values_rejected():
    with pytest.raises(ValueError):
        rolling_returns([100.0, float("nan"), 110.0], window=2)