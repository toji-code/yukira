import pytest

from src.alpha import jensens_alpha


def test_positive_jensens_alpha():
    result = jensens_alpha(
        portfolio_return=0.15,
        benchmark_return=0.10,
        risk_free_rate=0.05,
        portfolio_beta=1.0,
    )

    assert result == pytest.approx(0.05)


def test_zero_jensens_alpha():
    result = jensens_alpha(
        portfolio_return=0.10,
        benchmark_return=0.10,
        risk_free_rate=0.05,
        portfolio_beta=1.0,
    )

    assert result == pytest.approx(0.0)


def test_negative_jensens_alpha():
    result = jensens_alpha(
        portfolio_return=0.07,
        benchmark_return=0.10,
        risk_free_rate=0.05,
        portfolio_beta=1.0,
    )

    assert result == pytest.approx(-0.03)


def test_beta_changes_expected_return():
    result = jensens_alpha(
        portfolio_return=0.15,
        benchmark_return=0.10,
        risk_free_rate=0.05,
        portfolio_beta=2.0,
    )

    assert result == pytest.approx(0.0)


def test_beta_below_one():
    result = jensens_alpha(
        portfolio_return=0.10,
        benchmark_return=0.12,
        risk_free_rate=0.04,
        portfolio_beta=0.5,
    )

    expected = 0.10 - (0.04 + 0.5 * (0.12 - 0.04))

    assert result == pytest.approx(expected)


def test_negative_beta():
    result = jensens_alpha(
        portfolio_return=0.03,
        benchmark_return=0.08,
        risk_free_rate=0.05,
        portfolio_beta=-0.5,
    )

    expected = 0.03 - (0.05 + (-0.5) * (0.08 - 0.05))

    assert result == pytest.approx(expected)


def test_negative_returns():
    result = jensens_alpha(
        portfolio_return=-0.10,
        benchmark_return=-0.05,
        risk_free_rate=0.02,
        portfolio_beta=1.0,
    )

    expected = -0.10 - (0.02 + 1.0 * (-0.05 - 0.02))

    assert result == pytest.approx(expected)


def test_non_numeric_portfolio_return_rejected():
    with pytest.raises(TypeError):
        jensens_alpha("0.10", 0.10, 0.05, 1.0)


def test_non_numeric_benchmark_return_rejected():
    with pytest.raises(TypeError):
        jensens_alpha(0.10, "0.10", 0.05, 1.0)


def test_non_numeric_risk_free_rate_rejected():
    with pytest.raises(TypeError):
        jensens_alpha(0.10, 0.10, "0.05", 1.0)


def test_non_numeric_beta_rejected():
    with pytest.raises(TypeError):
        jensens_alpha(0.10, 0.10, 0.05, "1.0")