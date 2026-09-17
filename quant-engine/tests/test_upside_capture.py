import pytest

from src.upside_capture import upside_capture


def test_upside_capture():
    portfolio = [0.10, -0.05, 0.08, -0.03]
    benchmark = [0.05, -0.10, 0.06, -0.06]

    result = upside_capture(portfolio, benchmark)

    expected = ((1 + 0.10) * (1 + 0.08) - 1) / (
        (1 + 0.05) * (1 + 0.06) - 1
    ) * 100

    assert result == pytest.approx(expected)


def test_same_upside_as_benchmark():
    portfolio = [0.10]
    benchmark = [0.10]

    result = upside_capture(portfolio, benchmark)

    assert result == pytest.approx(100.0)


def test_greater_upside_than_benchmark():
    portfolio = [0.15]
    benchmark = [0.10]

    result = upside_capture(portfolio, benchmark)

    assert result == pytest.approx(150.0)


def test_less_upside_than_benchmark():
    portfolio = [0.05]
    benchmark = [0.10]

    result = upside_capture(portfolio, benchmark)

    assert result == pytest.approx(50.0)


def test_only_benchmark_up_periods_are_included():
    portfolio = [0.10, -0.05, 0.08]
    benchmark = [0.05, -0.10, 0.06]

    result = upside_capture(portfolio, benchmark)

    expected = ((1 + 0.10) * (1 + 0.08) - 1) / (
        (1 + 0.05) * (1 + 0.06) - 1
    ) * 100

    assert result == pytest.approx(expected)


def test_multiple_upside_periods_use_compounded_returns():
    portfolio = [0.10, 0.10]
    benchmark = [0.05, 0.05]

    result = upside_capture(portfolio, benchmark)

    expected = ((1.10 ** 2) - 1) / ((1.05 ** 2) - 1) * 100

    assert result == pytest.approx(expected)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        upside_capture([0.05, 0.10], [0.10])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        upside_capture([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        upside_capture([float("nan")], [0.10])


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        upside_capture([0.05], [float("inf")])


def test_no_benchmark_upside_periods_rejected():
    with pytest.raises(ValueError):
        upside_capture([-0.05, -0.10], [-0.02, -0.08])