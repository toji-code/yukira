import pytest
import math
from src.upside_beta import upside_beta
import numpy as np


def test_default_threshold_is_150():
    portfolio = [0.01 * i for i in range(160)]
    benchmark = [0.02 * i + 0.01 for i in range(160)]
    val = upside_beta(portfolio, benchmark)
    assert math.isfinite(val)


def test_150_observations_succeeds():
    portfolio = [0.01 * i for i in range(150)]
    benchmark = [0.02 * i + 0.01 for i in range(150)]
    val = upside_beta(portfolio, benchmark, min_upside_observations=150)
    assert math.isfinite(val)


def test_149_observations_fails():
    portfolio = [0.01] * 149
    benchmark = [0.01] * 149
    with pytest.raises(ValueError, match="at least 150"):
        upside_beta(portfolio, benchmark, min_upside_observations=149)


def test_explicit_150_succeeds():
    portfolio = [0.01 * i for i in range(150)]
    benchmark = [0.02 * i + 0.01 for i in range(150)]
    val = upside_beta(portfolio, benchmark, min_upside_observations=150)
    assert math.isfinite(val)


def test_negative_or_flat_benchmark_returns_strictly_excluded():
    # 150 positive benchmark days and 50 negative/flat benchmark days
    portfolio_pos = [0.02 * (i % 5) for i in range(150)]
    benchmark_pos = [0.01 * ((i % 5) + 1) for i in range(150)]

    portfolio_neg = [0.99] * 50  # Outlandish values that would distort beta if included
    benchmark_neg = [-0.05] * 25 + [0.0] * 25

    portfolio = portfolio_pos + portfolio_neg
    benchmark = benchmark_pos + benchmark_neg

    val = upside_beta(portfolio, benchmark, min_upside_observations=150)
    expected_val = upside_beta(portfolio_pos, benchmark_pos, min_upside_observations=150)
    assert val == pytest.approx(expected_val, abs=1e-12)


def test_empty_series_rejected():
    with pytest.raises(ValueError, match="cannot be empty"):
        upside_beta([], [])


def test_unequal_length_rejected():
    with pytest.raises(ValueError, match="equal length"):
        upside_beta([0.01, 0.02], [0.01])


def test_non_finite_rejected():
    with pytest.raises(ValueError, match="finite numeric"):
        upside_beta([0.01, float("nan")], [0.01, 0.02])
