import pytest

from src.capture import downside_capture


def test_downside_capture():
    portfolio = [-0.05, 0.10, -0.03, 0.08]
    benchmark = [-0.10, 0.08, -0.06, 0.05]

    result = downside_capture(portfolio, benchmark)

    expected = ((1 - 0.05) * (1 - 0.03) - 1) / (
        (1 - 0.10) * (1 - 0.06) - 1
    ) * 100

    assert result == pytest.approx(expected)


def test_less_downside_than_benchmark():
    portfolio = [-0.05]
    benchmark = [-0.10]

    result = downside_capture(portfolio, benchmark)

    assert result == pytest.approx(50.0)


def test_same_downside_as_benchmark():
    portfolio = [-0.10]
    benchmark = [-0.10]

    result = downside_capture(portfolio, benchmark)

    assert result == pytest.approx(100.0)


def test_greater_downside_than_benchmark():
    portfolio = [-0.15]
    benchmark = [-0.10]

    result = downside_capture(portfolio, benchmark)

    assert result == pytest.approx(150.0)


def test_only_benchmark_down_periods_are_included():
    portfolio = [0.20, -0.05, 0.10]
    benchmark = [0.10, -0.10, 0.05]

    result = downside_capture(portfolio, benchmark)

    assert result == pytest.approx(50.0)


def test_multiple_downside_periods_use_compounded_returns():
    portfolio = [-0.10, -0.10]
    benchmark = [-0.20, -0.20]

    result = downside_capture(portfolio, benchmark)

    expected = ((1 - 0.10) ** 2 - 1) / ((1 - 0.20) ** 2 - 1) * 100

    assert result == pytest.approx(expected)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        downside_capture([0.05, -0.05], [-0.10])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        downside_capture([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        downside_capture([float("nan")], [-0.10])


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        downside_capture([-0.05], [float("inf")])


def test_no_benchmark_downside_periods_rejected():
    with pytest.raises(ValueError):
        downside_capture([0.05, 0.10], [0.02, 0.08])