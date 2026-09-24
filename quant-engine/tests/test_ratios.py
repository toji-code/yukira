import pytest

from src.ratios import sharpe_ratio, sortino_ratio


def test_sharpe_ratio():
    returns = [0.10, -0.10]

    result = sharpe_ratio(
        returns,
        risk_free_rate=0.0,
        periods_per_year=1.0,
    )

    assert result == pytest.approx(0.0)


def test_sharpe_ratio_positive():
    returns = [0.10, 0.20, 0.15]

    result = sharpe_ratio(
        returns,
        risk_free_rate=0.0,
        periods_per_year=1.0,
    )

    assert result > 0.0


def test_sharpe_ratio_with_risk_free_rate():
    returns = [0.05, 0.10, 0.15]

    result = sharpe_ratio(
        returns,
        risk_free_rate=0.05,
        periods_per_year=1.0,
    )

    assert result > 0.0


def test_sharpe_ratio_rejects_insufficient_data():
    with pytest.raises(ValueError):
        sharpe_ratio(
            [0.10],
            risk_free_rate=0.0,
            periods_per_year=252.0,
        )


def test_sharpe_ratio_rejects_zero_volatility():
    with pytest.raises(ValueError):
        sharpe_ratio(
            [0.10, 0.10, 0.10],
            risk_free_rate=0.0,
            periods_per_year=252.0,
        )


def test_sharpe_ratio_rejects_invalid_periods():
    with pytest.raises(ValueError):
        sharpe_ratio(
            [0.10, 0.20],
            risk_free_rate=0.0,
            periods_per_year=0.0,
        )


def test_sharpe_ratio_with_risk_free_sequence():
    returns = [0.010, -0.005, 0.015, -0.002, 0.008]
    rf_series = [0.0002, 0.0002, 0.0002, 0.0002, 0.0002]

    result = sharpe_ratio(returns, risk_free_rate=rf_series, periods_per_year=252.0)
    assert result > 0.0

    # Test exact arithmetic equivalence
    excess = [r - rf for r, rf in zip(returns, rf_series)]
    import statistics
    expected = (statistics.mean(excess) / statistics.stdev(excess)) * (252.0 ** 0.5)
    assert result == pytest.approx(expected, abs=1e-15)


def test_sharpe_ratio_mismatched_sequence_length():
    with pytest.raises(ValueError, match="same length"):
        sharpe_ratio([0.01, 0.02, 0.03], risk_free_rate=[0.0002, 0.0002])


def test_sortino_ratio():
    returns = [0.10, -0.10]

    result = sortino_ratio(
        returns,
        target_return=0.0,
        periods_per_year=1.0,
    )

    assert result == pytest.approx(0.0)


def test_sortino_ratio_positive():
    returns = [0.10, 0.05, -0.02]

    result = sortino_ratio(
        returns,
        target_return=0.0,
        periods_per_year=1.0,
    )

    assert result > 0.0


def test_sortino_ratio_rejects_zero_downside():
    with pytest.raises(ValueError):
        sortino_ratio(
            [0.10, 0.20, 0.30],
            target_return=0.0,
            periods_per_year=252.0,
        )


def test_sortino_ratio_rejects_insufficient_data():
    with pytest.raises(ValueError):
        sortino_ratio(
            [0.10],
            target_return=0.0,
            periods_per_year=252.0,
        )