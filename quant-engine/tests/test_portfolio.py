import pytest

from src.portfolio import weighted_portfolio_return


def test_weighted_portfolio_return():
    weights = [0.60, 0.40]
    returns = [0.10, 0.05]

    result = weighted_portfolio_return(weights, returns)

    assert result == pytest.approx(0.08)


def test_equal_weight_portfolio():
    weights = [0.25, 0.25, 0.25, 0.25]
    returns = [0.10, 0.20, 0.00, -0.04]

    result = weighted_portfolio_return(weights, returns)

    assert result == pytest.approx(0.065)


def test_negative_asset_return():
    weights = [0.70, 0.30]
    returns = [0.10, -0.20]

    result = weighted_portfolio_return(weights, returns)

    assert result == pytest.approx(0.01)


def test_single_asset():
    result = weighted_portfolio_return([1.0], [0.15])

    assert result == pytest.approx(0.15)


def test_weights_and_returns_must_have_equal_length():
    with pytest.raises(ValueError):
        weighted_portfolio_return([0.60, 0.40], [0.10])


def test_empty_inputs_rejected():
    with pytest.raises(ValueError):
        weighted_portfolio_return([], [])


def test_negative_weights_rejected():
    with pytest.raises(ValueError):
        weighted_portfolio_return([1.10, -0.10], [0.10, 0.05])


def test_weights_must_sum_to_one():
    with pytest.raises(ValueError):
        weighted_portfolio_return([0.60, 0.30], [0.10, 0.05])


def test_non_finite_weight_rejected():
    with pytest.raises(ValueError):
        weighted_portfolio_return([float("nan"), 1.0], [0.10, 0.05])


def test_non_finite_return_rejected():
    with pytest.raises(ValueError):
        weighted_portfolio_return([0.50, 0.50], [0.10, float("inf")])