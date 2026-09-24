import numpy as np
import pytest

from src.downside_beta import downside_beta


def test_downside_beta_equals_two():
    portfolio = [0.05, -0.02, 0.04, -0.06]
    benchmark = [0.03, -0.01, 0.02, -0.03]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_downside_beta_equals_one():
    portfolio = [0.05, -0.01, 0.04, -0.03]
    benchmark = [0.03, -0.01, 0.02, -0.03]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(1.0)


def test_upside_periods_are_excluded():
    portfolio = [0.20, -0.02, 0.30, -0.04]
    benchmark = [0.10, -0.01, 0.20, -0.02]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(2.0)


def test_negative_downside_beta():
    portfolio = [0.05, -0.01, 0.02, -0.03]
    benchmark = [0.10, -0.02, 0.04, -0.06]

    result = downside_beta(portfolio, benchmark)

    assert result == pytest.approx(0.5)


def test_unequal_lengths_rejected():
    with pytest.raises(ValueError):
        downside_beta([0.01, -0.02], [-0.01])


def test_empty_series_rejected():
    with pytest.raises(ValueError):
        downside_beta([], [])


def test_non_finite_portfolio_return_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [float("nan"), -0.02, -0.03],
            [-0.01, -0.02, -0.03],
        )


def test_non_finite_benchmark_return_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [-0.01, -0.02, -0.03],
            [float("nan"), -0.02, -0.03],
        )


def test_insufficient_downside_observations_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [0.05, -0.02, 0.03],
            [0.04, -0.01, 0.02],
        )


def test_no_downside_observations_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [0.05, 0.02, 0.03],
            [0.04, 0.01, 0.02],
        )


def test_zero_downside_benchmark_variance_rejected():
    with pytest.raises(ValueError):
        downside_beta(
            [-0.01, -0.02, -0.03],
            [-0.02, -0.02, -0.02],
        )


# ==============================================================================
# Phase 2O / M2N-07: Downside Beta Specification Tests
# ==============================================================================

def test_zero_benchmark_return_is_strictly_excluded():
    # Benchmark returns: [-0.01, 0.00, 0.02, -0.03]
    # Only -0.01 and -0.03 must be included. 0.00 and 0.02 are excluded.
    portfolio = [-0.015, 0.050, 0.020, -0.045]
    benchmark = [-0.010, 0.000, 0.020, -0.030]

    # Downside observations:
    # t=0: Rp = -0.015, Rb = -0.010
    # t=3: Rp = -0.045, Rb = -0.030
    # Downside beta = (-0.015 - (-0.045)) / (-0.010 - (-0.030)) = 0.030 / 0.020 = 1.50
    result = downside_beta(portfolio, benchmark)
    assert result == pytest.approx(1.50, abs=1e-12)


def test_positive_benchmark_return_is_strictly_excluded():
    # Only negative Rb (< 0.0) are considered
    portfolio = [-0.008, 0.100, -0.024]
    benchmark = [-0.010, 0.050, -0.030]

    # Downside pairs: (-0.008, -0.010), (-0.024, -0.030)
    # Slope = (-0.008 - (-0.024)) / (-0.010 - (-0.030)) = 0.016 / 0.020 = 0.80
    result = downside_beta(portfolio, benchmark)
    assert result == pytest.approx(0.80, abs=1e-12)


def test_downside_beta_hand_verifiable_vector():
    # 5 downside observations
    # Rb_down = [-0.01, -0.02, -0.03, -0.04, -0.05] -> mean = -0.03
    # Rp_down = [-0.012, -0.024, -0.036, -0.048, -0.060] -> Rp = 1.2 * Rb
    # Beta_down = 1.20 exactly
    rp = [-0.012, 0.010, -0.024, 0.020, -0.036, 0.000, -0.048, -0.060]
    rb = [-0.010, 0.015, -0.020, 0.025, -0.030, 0.000, -0.040, -0.050]

    result = downside_beta(rp, rb)
    assert result == pytest.approx(1.20, abs=1e-12)


def test_downside_beta_exactly_100_downside_observations():
    # 100 downside observations out of 250 total
    rb_down = [-0.005 * ((i % 10) + 1) for i in range(100)]
    rp_down = [0.95 * x for x in rb_down]

    rb_up = [0.010 * ((i % 10) + 1) for i in range(150)]
    rp_up = [1.20 * x for x in rb_up]

    rb = rb_down + rb_up
    rp = rp_down + rp_up

    # Test with min_downside_observations=100
    result = downside_beta(rp, rb, min_downside_observations=100)
    assert result == pytest.approx(0.95, abs=1e-12)


def test_downside_beta_below_100_observations_rejected_when_configured():
    # 99 downside observations
    rb_down = [-0.005 * ((i % 10) + 1) for i in range(99)]
    rp_down = [0.95 * x for x in rb_down]

    with pytest.raises(ValueError, match="at least 100 benchmark downside observations are required"):
        downside_beta(rp_down, rb_down, min_downside_observations=100)


def test_downside_beta_deterministic_repeatability():
    rng = np.random.default_rng(456)
    n = 300
    rb = rng.normal(0.0002, 0.012, n).tolist()
    rp = [1.10 * x if x < 0 else 0.85 * x for x in rb]

    b1 = downside_beta(rp, rb)
    b2 = downside_beta(rp, rb)
    b3 = downside_beta(rp, rb)

    assert b1 == b2 == b3