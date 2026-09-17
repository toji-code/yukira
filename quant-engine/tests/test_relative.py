import pytest

from src.relative import relative_returns


def test_relative_returns():
    portfolio = [100, 110, 105]
    benchmark = [100, 105, 102]

    result = relative_returns(portfolio, benchmark)

    assert result == pytest.approx([
        0.05,
        105 / 110 - 102 / 105,
    ])


def test_relative_returns_outperformance():
    portfolio = [100, 110]
    benchmark = [100, 105]

    result = relative_returns(portfolio, benchmark)

    assert result == pytest.approx([0.05])


def test_relative_returns_underperformance():
    portfolio = [100, 105]
    benchmark = [100, 110]

    result = relative_returns(portfolio, benchmark)

    assert result == pytest.approx([-0.05])


def test_equal_performance():
    portfolio = [100, 110, 121]
    benchmark = [100, 110, 121]

    result = relative_returns(portfolio, benchmark)

    assert result == pytest.approx([0.0, 0.0])


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        relative_returns([100, 110], [100, 105, 102])


def test_single_observation_rejected():
    with pytest.raises(ValueError):
        relative_returns([100], [100])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        relative_returns([], [])


def test_zero_portfolio_value_rejected():
    with pytest.raises(ValueError):
        relative_returns([100, 0], [100, 105])


def test_zero_benchmark_value_rejected():
    with pytest.raises(ValueError):
        relative_returns([100, 110], [100, 0])


def test_negative_values_rejected():
    with pytest.raises(ValueError):
        relative_returns([100, -110], [100, 105])


def test_non_finite_values_rejected():
    with pytest.raises(ValueError):
        relative_returns([100, float("nan")], [100, 105])