import pytest

from src.treynor import treynor_ratio


def test_positive_treynor_ratio():
    result = treynor_ratio(
        portfolio_return=0.15,
        risk_free_rate=0.05,
        portfolio_beta=1.0,
    )

    assert result == pytest.approx(0.10)


def test_negative_treynor_ratio():
    result = treynor_ratio(
        portfolio_return=0.03,
        risk_free_rate=0.05,
        portfolio_beta=1.0,
    )

    assert result == pytest.approx(-0.02)


def test_beta_changes_treynor_ratio():
    result = treynor_ratio(
        portfolio_return=0.15,
        risk_free_rate=0.05,
        portfolio_beta=2.0,
    )

    assert result == pytest.approx(0.05)


def test_negative_beta():
    result = treynor_ratio(
        portfolio_return=0.03,
        risk_free_rate=0.05,
        portfolio_beta=-1.0,
    )

    assert result == pytest.approx(0.02)


def test_zero_excess_return():
    result = treynor_ratio(
        portfolio_return=0.05,
        risk_free_rate=0.05,
        portfolio_beta=1.2,
    )

    assert result == pytest.approx(0.0)


def test_fractional_beta():
    result = treynor_ratio(
        portfolio_return=0.10,
        risk_free_rate=0.04,
        portfolio_beta=0.5,
    )

    assert result == pytest.approx(0.12)


def test_zero_beta_rejected():
    with pytest.raises(ValueError):
        treynor_ratio(
            portfolio_return=0.10,
            risk_free_rate=0.04,
            portfolio_beta=0.0,
        )


def test_non_numeric_portfolio_return_rejected():
    with pytest.raises(TypeError):
        treynor_ratio(
            portfolio_return="0.10",
            risk_free_rate=0.04,
            portfolio_beta=1.0,
        )


def test_non_numeric_risk_free_rate_rejected():
    with pytest.raises(TypeError):
        treynor_ratio(
            portfolio_return=0.10,
            risk_free_rate="0.04",
            portfolio_beta=1.0,
        )


def test_non_numeric_beta_rejected():
    with pytest.raises(TypeError):
        treynor_ratio(
            portfolio_return=0.10,
            risk_free_rate=0.04,
            portfolio_beta="1.0",
        )


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        treynor_ratio(
            portfolio_return=float("nan"),
            risk_free_rate=0.04,
            portfolio_beta=1.0,
        )


def test_non_finite_beta_rejected():
    with pytest.raises(ValueError):
        treynor_ratio(
            portfolio_return=0.10,
            risk_free_rate=0.04,
            portfolio_beta=float("inf"),
        )