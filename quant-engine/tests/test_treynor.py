import math
from datetime import date, datetime, timezone
import pytest

from src.treynor import treynor_numerator, treynor_ratio
from src.risk_free import RiskFreeObservation, align_risk_free_series


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


# ==============================================================================
# Phase 2O / M2N-05: Approved Treynor Numerator & Sequence Tests
# ==============================================================================

def test_treynor_numerator_positive_excess_return():
    # Mean daily excess return = 0.0005 -> Annualized = 0.0005 * 252 = 0.126
    portfolio_returns = [0.0010, 0.0012, 0.0008, 0.0015, 0.0010]
    risk_free_rates = [0.0005, 0.0005, 0.0005, 0.0005, 0.0005]

    num = treynor_numerator(portfolio_returns, risk_free_rates, periods_per_year=252.0)
    # Mean excess = (0.0005 + 0.0007 + 0.0003 + 0.0010 + 0.0005) / 5 = 0.0030 / 5 = 0.0006
    # 0.0006 * 252 = 0.1512
    assert num == pytest.approx(0.1512, abs=1e-12)


def test_treynor_numerator_zero_excess_return():
    portfolio_returns = [0.0002, 0.0004, 0.0006]
    risk_free_rates = [0.0002, 0.0004, 0.0006]

    num = treynor_numerator(portfolio_returns, risk_free_rates, periods_per_year=252.0)
    assert num == pytest.approx(0.0, abs=1e-15)


def test_treynor_numerator_negative_excess_return():
    portfolio_returns = [0.0001, 0.0002, 0.0001]
    risk_free_rates = [0.0003, 0.0004, 0.0003]

    num = treynor_numerator(portfolio_returns, risk_free_rates, periods_per_year=252.0)
    # Excess: -0.0002, -0.0002, -0.0002 -> Mean = -0.0002 -> * 252 = -0.0504
    assert num == pytest.approx(-0.0504, abs=1e-12)


def test_treynor_numerator_sequence_length_mismatch_rejected():
    with pytest.raises(ValueError, match="equal length"):
        treynor_numerator([0.01, 0.02], [0.005])


def test_treynor_numerator_empty_rejected():
    with pytest.raises(ValueError, match="empty"):
        treynor_numerator([], [])


def test_treynor_numerator_non_finite_rejected():
    with pytest.raises(ValueError):
        treynor_numerator([0.01, float("nan")], [0.005, 0.005])

    with pytest.raises(ValueError):
        treynor_numerator([0.01, 0.02], [0.005, float("inf")])


def test_treynor_ratio_sequence_evaluation():
    portfolio_returns = [0.0010, 0.0012, 0.0008, 0.0015, 0.0010]
    risk_free_rates = [0.0005, 0.0005, 0.0005, 0.0005, 0.0005]
    portfolio_beta = 1.20

    # Numerator = 0.1512
    # Treynor = 0.1512 / 1.20 = 0.1260
    tr = treynor_ratio(portfolio_returns, risk_free_rates, portfolio_beta=portfolio_beta, periods_per_year=252.0)
    assert tr == pytest.approx(0.1260, abs=1e-12)


def test_treynor_numerator_700_observations_threshold():
    # Exactly 700 observations
    n = 700
    portfolio_returns = [0.0008] * n
    risk_free_rates = [0.0003] * n
    num = treynor_numerator(portfolio_returns, risk_free_rates, periods_per_year=252.0)
    # Mean excess = 0.0005 -> * 252 = 0.126
    assert num == pytest.approx(0.126, abs=1e-12)


def test_treynor_deterministic_repeatability():
    # Verify exact deterministic numerical output across multiple runs
    p = [0.0012, -0.0005, 0.0020, 0.0003, -0.0010] * 140  # 700 obs
    rf = [0.00028, 0.00028, 0.00028, 0.00028, 0.00028] * 140
    beta = 0.95

    res1 = treynor_ratio(p, rf, portfolio_beta=beta)
    res2 = treynor_ratio(p, rf, portfolio_beta=beta)
    res3 = treynor_ratio(p, rf, portfolio_beta=beta)

    assert res1 == res2 == res3


def test_treynor_with_m2n02_risk_free_kernel_and_pit():
    # Construct a series of risk-free quotes and resolve via M2N-02 kernel
    quotes = [
        RiskFreeObservation(
            effective_date=date(2024, 1, 1),
            availability_time=datetime(2024, 1, 1, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.0695,  # 6.95%
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=date(2024, 1, 2),
            availability_time=datetime(2024, 1, 2, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.0698,
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=date(2024, 1, 3),
            availability_time=datetime(2024, 1, 3, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.0700,
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

    portfolio_returns = [0.0050, 0.0030]
    portfolio_beta = 1.05

    # Interval 1 (Jan 1 -> Jan 2): rf = 0.0695 * 1 / 365.0
    # Interval 2 (Jan 2 -> Jan 3): rf = 0.0698 * 1 / 365.0
    expected_rf = [0.0695 / 365.0, 0.0698 / 365.0]
    assert rf_series == pytest.approx(expected_rf, abs=1e-15)

    tr = treynor_ratio(portfolio_returns, rf_series, portfolio_beta=portfolio_beta)
    expected_excess = [(0.0050 - expected_rf[0]), (0.0030 - expected_rf[1])]
    expected_num = (sum(expected_excess) / 2.0) * 252.0
    expected_tr = expected_num / portfolio_beta

    assert tr == pytest.approx(expected_tr, abs=1e-12)


def test_treynor_pit_post_knowledge_cutoff_rejection():
    # If a quote was published after knowledge cutoff, align_risk_free_series excludes it
    quotes = [
        RiskFreeObservation(
            effective_date=date(2024, 1, 1),
            availability_time=datetime(2024, 1, 1, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.0695,
            revision_seq=1,
        ),
        # Available only at 18:00 on Jan 2
        RiskFreeObservation(
            effective_date=date(2024, 1, 2),
            availability_time=datetime(2024, 1, 2, 18, 0, tzinfo=timezone.utc),
            quoted_yield=0.0698,
            revision_seq=1,
        ),
    ]

    trading_dates = [date(2024, 1, 1), date(2024, 1, 2), date(2024, 1, 3)]
    # Knowledge cutoff is 12:00 on Jan 2 -> quote on Jan 2 is NOT available at cutoff
    knowledge_cutoff = datetime(2024, 1, 2, 12, 0, tzinfo=timezone.utc)
    analysis_cutoff = date(2024, 1, 3)

    rf_series = align_risk_free_series(
        observation_dates=trading_dates,
        risk_free_observations=quotes,
        analysis_cutoff=analysis_cutoff,
        knowledge_cutoff=knowledge_cutoff,
    )

    # For Jan 2, lookback finds Jan 1 quote (0.0695)
    # For Jan 3, lookback finds Jan 1 quote (0.0695) because Jan 2 quote is post-knowledge-cutoff!
    assert rf_series[0] == pytest.approx(0.0695 / 365.0, abs=1e-15)
    assert rf_series[1] == pytest.approx(0.0695 / 365.0, abs=1e-15)