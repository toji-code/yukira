import pytest

from src.benchmarks import relative_return


def test_relative_return_positive():
    assert relative_return(0.15, 0.10) == pytest.approx(0.05)


def test_relative_return_negative():
    assert relative_return(0.08, 0.12) == pytest.approx(-0.04)


def test_relative_return_equal():
    assert relative_return(0.10, 0.10) == pytest.approx(0.0)


def test_negative_portfolio_return():
    assert relative_return(-0.05, 0.02) == pytest.approx(-0.07)


def test_negative_benchmark_return():
    assert relative_return(0.05, -0.03) == pytest.approx(0.08)


def test_non_numeric_portfolio_return_rejected():
    with pytest.raises(TypeError):
        relative_return("0.10", 0.05)


def test_non_numeric_benchmark_return_rejected():
    with pytest.raises(TypeError):
        relative_return(0.10, "0.05")