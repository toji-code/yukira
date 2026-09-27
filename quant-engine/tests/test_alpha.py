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


# ==============================================================================
# Tests for Option B: Econometric Time-Series OLS Intercept (REL-03)
# ==============================================================================

from src.alpha import jensens_alpha_ols


def test_jensens_alpha_ols_exact_intercept_annualized():
    # Model: y = alpha + beta * x
    # Let x = [0.01, -0.01, 0.02, -0.02] (mean(x) = 0.0)
    # Let beta = 1.2, alpha_daily = 0.001
    # Then y = 0.001 + 1.2 * x = [0.013, -0.011, 0.025, -0.023]
    x = [0.01, -0.01, 0.02, -0.02]
    y = [0.001 + 1.2 * val for val in x]

    # Annualized alpha = 0.001 * 252 = 0.252
    result = jensens_alpha_ols(y, x, periods_per_year=252.0)
    assert result == pytest.approx(0.252, rel=1e-9)


def test_jensens_alpha_ols_with_nonzero_mean_benchmark():
    # x = [0.02, 0.00, 0.03, -0.01] -> mean(x) = 0.01
    # beta = 1.5, alpha_daily = 0.0005
    # y = 0.0005 + 1.5 * x = [0.0305, 0.0005, 0.0455, -0.0145]
    x = [0.02, 0.00, 0.03, -0.01]
    y = [0.0005 + 1.5 * val for val in x]

    # Annualized alpha = 0.0005 * 252 = 0.126
    result = jensens_alpha_ols(y, x, periods_per_year=252.0)
    assert result == pytest.approx(0.126, rel=1e-9)


def test_jensens_alpha_ols_daily_risk_free_subtraction():
    # Raw returns with daily risk-free rates
    # p = [0.012, 0.002, 0.022, -0.018]
    # b = [0.011, -0.009, 0.021, -0.019]
    # rf = [0.001, 0.001, 0.001, 0.001]
    # Excess:
    # y = p - rf = [0.011, 0.001, 0.021, -0.019] (mean(y) = 0.0035)
    # x = b - rf = [0.010, -0.010, 0.020, -0.020] (mean(x) = 0.0)
    # Cov(x, y) = 0.0009 / 3, Var(x) = 0.0010 / 3 => beta = 0.9
    # alpha_daily = 0.0035 - 0.9 * 0.0 = 0.0035
    # alpha_annual = 0.0035 * 252 = 0.882
    p = [0.012, 0.002, 0.022, -0.018]
    b = [0.011, -0.009, 0.021, -0.019]
    rf = [0.001, 0.001, 0.001, 0.001]

    result = jensens_alpha_ols(p, b, risk_free_rates=rf, periods_per_year=252.0)
    assert result == pytest.approx(0.882, rel=1e-9)


def test_jensens_alpha_ols_with_supplied_beta():
    # If M2N-06 beta is passed in directly, it must produce identical result
    p = [0.012, 0.002, 0.022, -0.018]
    b = [0.011, -0.009, 0.021, -0.019]
    rf = [0.001, 0.001, 0.001, 0.001]

    result = jensens_alpha_ols(p, b, risk_free_rates=rf, portfolio_beta=0.9, periods_per_year=252.0)
    assert result == pytest.approx(0.882, rel=1e-9)


def test_jensens_alpha_ols_insufficient_observations():
    with pytest.raises(ValueError, match="at least two observations are required"):
        jensens_alpha_ols([0.01], [0.01])


def test_jensens_alpha_ols_length_mismatch():
    with pytest.raises(ValueError, match="must have equal length"):
        jensens_alpha_ols([0.01, 0.02], [0.01])


def test_jensens_alpha_ols_degenerate_benchmark_variance():
    # Constant benchmark return => zero variance
    with pytest.raises(ValueError, match="benchmark variance must be greater than zero"):
        jensens_alpha_ols([0.01, 0.02, 0.03], [0.01, 0.01, 0.01])


def test_jensens_alpha_ols_non_finite_inputs():
    with pytest.raises(ValueError, match="must be finite"):
        jensens_alpha_ols([0.01, float("nan")], [0.01, 0.02])
    with pytest.raises(ValueError, match="must be finite"):
        jensens_alpha_ols([0.01, 0.02], [0.01, float("inf")])