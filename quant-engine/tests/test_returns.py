import pytest

from src.returns import cagr, cagr_from_calendar_days, period_return


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


def test_cagr_from_calendar_days_365():
    # 10% gain over 365 calendar days
    result = cagr_from_calendar_days(100.0, 110.0, 365)
    expected = 1.10 ** (365.25 / 365.0) - 1.0
    assert result == pytest.approx(expected, abs=1e-15)


def test_cagr_from_calendar_days_366_leap():
    # 10% gain over 366 calendar days (leap year)
    result = cagr_from_calendar_days(100.0, 110.0, 366)
    expected = 1.10 ** (365.25 / 366.0) - 1.0
    assert result == pytest.approx(expected, abs=1e-15)


def test_cagr_from_calendar_days_multi_year_3y():
    # 50% gain over 1096 calendar days (3Y with 1 leap year)
    result = cagr_from_calendar_days(100.0, 150.0, 1096)
    expected = 1.50 ** (365.25 / 1096.0) - 1.0
    assert result == pytest.approx(expected, abs=1e-15)


def test_cagr_from_calendar_days_constant_nav():
    result = cagr_from_calendar_days(100.0, 100.0, 730)
    assert result == 0.0


def test_cagr_from_calendar_days_parameter_validation():
    with pytest.raises(ValueError):
        cagr_from_calendar_days(0.0, 110.0, 365)

    with pytest.raises(ValueError):
        cagr_from_calendar_days(100.0, -10.0, 365)

    with pytest.raises(ValueError):
        cagr_from_calendar_days(100.0, 110.0, 0)