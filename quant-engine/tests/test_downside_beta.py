import pytest
from src.downside_beta import downside_beta
import numpy as np

def test_default_threshold_is_100():
    # Downside dev > 0 requires varied benchmark returns
    portfolio = [0.01 * i for i in range(105)]
    benchmark = [-0.02 * i - 0.01 for i in range(105)]
    # Should not raise ValueError
    downside_beta(portfolio, benchmark)

def test_100_observations_succeeds():
    portfolio = [0.01 * i for i in range(100)]
    benchmark = [-0.02 * i - 0.01 for i in range(100)]
    downside_beta(portfolio, benchmark, min_downside_observations=100)

def test_99_observations_fails():
    portfolio = [0.01] * 99
    benchmark = [-0.01] * 99
    with pytest.raises(ValueError, match="at least 100"):
        downside_beta(portfolio, benchmark, min_downside_observations=99)

def test_explicit_100_succeeds():
    portfolio = [0.01 * i for i in range(100)]
    benchmark = [-0.02 * i - 0.01 for i in range(100)]
    downside_beta(portfolio, benchmark, min_downside_observations=100)

def test_explicit_99_fails():
    portfolio = [0.01] * 99
    benchmark = [-0.01] * 99
    with pytest.raises(ValueError, match="at least 100"):
        downside_beta(portfolio, benchmark, min_downside_observations=99)

def test_explicit_2_fails():
    portfolio = [0.01, -0.02]
    benchmark = [-0.01, -0.03]
    with pytest.raises(ValueError, match="at least 100"):
        downside_beta(portfolio, benchmark, min_downside_observations=2)

def test_negative_threshold_fails():
    portfolio = [0.01] * 100
    benchmark = [-0.01] * 100
    with pytest.raises(ValueError, match="at least 100"):
        downside_beta(portfolio, benchmark, min_downside_observations=-1)

def test_zero_threshold_fails():
    portfolio = [0.01] * 100
    benchmark = [-0.01] * 100
    with pytest.raises(ValueError, match="at least 100"):
        downside_beta(portfolio, benchmark, min_downside_observations=0)
