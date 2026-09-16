import pytest

from src.returns import cagr, period_return


def test_period_return_positive():
    result = period_return(100.0, 110.0)

    assert result == pytest.approx(0.10)


def test_period_return_negative():
    result = period_return(100.0, 90.0)

    assert result == pytest.approx(-0.10)


def test_period_return_zero_change():
    result = period_return(100.0, 100.0)

    assert result == pytest.approx(0.0)


def test_period_return_rejects_zero_start():
    with pytest.raises(ValueError):
        period_return(0.0, 100.0)


def test_period_return_rejects_negative_start():
    with pytest.raises(ValueError):
        period_return(-100.0, 100.0)


def test_cagr():
    result = cagr(100.0, 121.0, 2.0)

    assert result == pytest.approx(0.10)


def test_cagr_with_fractional_years():
    result = cagr(100.0, 110.0, 0.5)

    assert result == pytest.approx(0.21)


def test_cagr_rejects_invalid_years():
    with pytest.raises(ValueError):
        cagr(100.0, 110.0, 0.0)


def test_cagr_rejects_non_positive_values():
    with pytest.raises(ValueError):
        cagr(0.0, 110.0, 1.0)

    with pytest.raises(ValueError):
        cagr(100.0, 0.0, 1.0)