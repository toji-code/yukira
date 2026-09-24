import math
from datetime import date, datetime, timezone
import numpy as np
import pytest

from src.beta import beta
from src.risk_free import RiskFreeObservation, align_risk_free_series


def test_beta_equals_one_for_identical_returns():
    portfolio = [0.01, 0.02, -0.01, 0.03]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_beta_equals_two_for_double_benchmark_movements():
    portfolio = [0.02, 0.04, -0.02, 0.06]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_beta_equals_zero_for_uncorrelated_constant_portfolio():
    portfolio = [0.02, 0.02, 0.02, 0.02]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(0.0)


def test_negative_beta():
    portfolio = [-0.01, -0.02, 0.01, -0.03]
    benchmark = [0.01, 0.02, -0.01, 0.03]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(-1.0)


def test_beta_with_shifted_returns():
    benchmark = [0.01, 0.02, -0.01, 0.03]
    portfolio = [0.06, 0.07, 0.04, 0.08]

    result = beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02], [0.01])


def test_insufficient_observations_rejected():
    with pytest.raises(ValueError):
        beta([0.01], [0.02])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        beta([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        beta([0.01, float("nan")], [0.01, 0.02])


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02], [0.01, float("inf")])


def test_zero_benchmark_variance_rejected():
    with pytest.raises(ValueError):
        beta([0.01, 0.02, 0.03], [0.02, 0.02, 0.02])


def test_beta_scale_invariance_under_annualization_factors():
    # Per M2N-01, Beta is dimensionless and scale-invariant (no annualization multiplier)
    portfolio = [0.012, -0.008, 0.015, -0.003, 0.007]
    benchmark = [0.010, -0.007, 0.012, -0.002, 0.006]

    raw_beta = beta(portfolio, benchmark)

    # Scaling by sqrt(252)
    sqrt_252 = 252.0 ** 0.5
    scaled_p_sqrt = [r * sqrt_252 for r in portfolio]
    scaled_b_sqrt = [r * sqrt_252 for r in benchmark]
    scaled_beta_sqrt = beta(scaled_p_sqrt, scaled_b_sqrt)
    assert scaled_beta_sqrt == pytest.approx(raw_beta, abs=1e-15)

    # Scaling by 252
    scaled_p_252 = [r * 252.0 for r in portfolio]
    scaled_b_252 = [r * 252.0 for r in benchmark]
    scaled_beta_252 = beta(scaled_p_252, scaled_b_252)
    assert scaled_beta_252 == pytest.approx(raw_beta, abs=1e-15)


# ==============================================================================
# Phase 2O / M2N-06: Excess-Return OLS Beta & Analytical Tests
# ==============================================================================

def test_excess_return_beta_hand_calculated_vector():
    # Construct exact hand-calculated test vectors
    # Let y_t = Rp - Rf, x_t = Rb - Rf
    # Let x = [0.01, -0.02, 0.03, -0.01, 0.04] -> mean = 0.05 / 5 = 0.01
    # x - x_bar = [0.00, -0.03, 0.02, -0.02, 0.03]
    # Var(x) = (0 + 0.0009 + 0.0004 + 0.0004 + 0.0009) / 4 = 0.0026 / 4 = 0.00065
    # Let y = 0.002 + 1.5 * x -> y = [0.017, -0.028, 0.047, -0.013, 0.062]
    # Then beta must be exactly 1.50
    rf = [0.0003, 0.0003, 0.0003, 0.0003, 0.0003]
    x_excess = [0.01, -0.02, 0.03, -0.01, 0.04]
    y_excess = [0.017, -0.028, 0.047, -0.013, 0.062]

    benchmark_returns = [x + r for x, r in zip(x_excess, rf)]
    portfolio_returns = [y + r for y, r in zip(y_excess, rf)]

    b = beta(portfolio_returns, benchmark_returns, risk_free_rates=rf)
    assert b == pytest.approx(1.50, abs=1e-12)


def test_excess_return_beta_numpy_independent_cross_check():
    # Generate a pseudorandom series and compare with np.polyfit OLS slope
    rng = np.random.default_rng(42)
    n = 750
    rf = rng.uniform(0.0002, 0.0003, n).tolist()
    rb = rng.normal(0.0005, 0.012, n).tolist()
    # True alpha = 0.0002, true beta = 1.15, plus residual noise
    noise = rng.normal(0, 0.002, n).tolist()
    rp = [rf_i + 0.0002 + 1.15 * (rb_i - rf_i) + eps for rf_i, rb_i, eps in zip(rf, rb, noise)]

    calculated_beta = beta(rp, rb, risk_free_rates=rf)

    # Independent OLS via numpy
    x = np.array(rb) - np.array(rf)
    y = np.array(rp) - np.array(rf)
    expected_beta, _ = np.polyfit(x, y, 1)

    assert calculated_beta == pytest.approx(float(expected_beta), abs=1e-12)


def test_excess_return_beta_exactly_700_observations():
    n = 700
    rf = [0.00028] * n
    rb = [0.010, -0.008, 0.015, -0.005, 0.002] * 140
    rp = [0.012, -0.010, 0.018, -0.006, 0.0025] * 140

    b = beta(rp, rb, risk_free_rates=rf)
    assert math.isfinite(b)
    assert b > 0.0


def test_excess_return_beta_zero_excess_variance_rejected():
    # If benchmark excess returns are constant, variance is zero -> reject
    rf = [0.0002, 0.0003, 0.0004]
    rb = [0.0052, 0.0053, 0.0054]  # Rb - Rf = 0.0050 (constant)
    rp = [0.010, 0.012, 0.014]

    with pytest.raises(ValueError, match="variance must be greater than zero"):
        beta(rp, rb, risk_free_rates=rf)


def test_excess_return_beta_deterministic_repeatability():
    rng = np.random.default_rng(123)
    n = 700
    rf = rng.uniform(0.0002, 0.0003, n).tolist()
    rb = rng.normal(0.0005, 0.01, n).tolist()
    rp = [rf_i + 0.90 * (rb_i - rf_i) for rf_i, rb_i in zip(rf, rb)]

    b1 = beta(rp, rb, risk_free_rates=rf)
    b2 = beta(rp, rb, risk_free_rates=rf)
    b3 = beta(rp, rb, risk_free_rates=rf)

    assert b1 == b2 == b3


def test_excess_return_beta_with_m2n02_risk_free_and_pit():
    quotes = [
        RiskFreeObservation(
            effective_date=date(2024, 1, 1),
            availability_time=datetime(2024, 1, 1, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.070,
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=date(2024, 1, 2),
            availability_time=datetime(2024, 1, 2, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.071,
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=date(2024, 1, 3),
            availability_time=datetime(2024, 1, 3, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.072,
            revision_seq=1,
        ),
    ]

    trading_dates = [date(2024, 1, 1), date(2024, 1, 2), date(2024, 1, 3)]
    knowledge_cutoff = datetime(2024, 1, 3, 23, 59, tzinfo=timezone.utc)
    analysis_cutoff = date(2024, 1, 3)

    rf_series = align_risk_free_series(
        observation_dates=trading_dates,
        risk_free_observations=quotes,
        analysis_cutoff=analysis_cutoff,
        knowledge_cutoff=knowledge_cutoff,
    )

    rp = [0.012, -0.005]
    rb = [0.010, -0.004]

    b = beta(rp, rb, risk_free_rates=rf_series)

    # Independent hand calculation
    y = [rp[0] - rf_series[0], rp[1] - rf_series[1]]
    x = [rb[0] - rf_series[0], rb[1] - rf_series[1]]
    x_mean = (x[0] + x[1]) / 2.0
    y_mean = (y[0] + y[1]) / 2.0
    cov = ((x[0] - x_mean) * (y[0] - y_mean) + (x[1] - x_mean) * (y[1] - y_mean))
    var = ((x[0] - x_mean) ** 2 + (x[1] - x_mean) ** 2)
    expected_beta = cov / var

    assert b == pytest.approx(expected_beta, abs=1e-15)