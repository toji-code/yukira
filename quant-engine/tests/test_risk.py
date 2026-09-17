import pytest

from src.risk import drawdown_series, maximum_drawdown


def test_drawdown_series():
    values = [100.0, 110.0, 99.0, 121.0]

    result = drawdown_series(values)

    assert result == pytest.approx(
        [0.0, 0.0, -0.10, 0.0]
    )


def test_maximum_drawdown():
    values = [100.0, 110.0, 99.0, 121.0]

    result = maximum_drawdown(values)

    assert result == pytest.approx(-0.10)


def test_no_drawdown():
    values = [100.0, 105.0, 110.0, 120.0]

    assert maximum_drawdown(values) == pytest.approx(0.0)


def test_drawdown_after_multiple_peaks():
    values = [100.0, 120.0, 108.0, 130.0, 104.0]

    result = maximum_drawdown(values)

    assert result == pytest.approx(-0.20)


def test_single_observation():
    assert maximum_drawdown([100.0]) == pytest.approx(0.0)


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        maximum_drawdown([])


def test_negative_values_rejected():
    with pytest.raises(ValueError):
        maximum_drawdown([100.0, -10.0, 110.0])


def test_non_finite_values_rejected():
    with pytest.raises(ValueError):
        maximum_drawdown([100.0, float("nan"), 110.0])