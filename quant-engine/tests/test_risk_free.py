"""
Unit tests for approved FBIL 91-Day Treasury Bill Risk-Free Rate calculations (M2N-02).
"""

import datetime
import pytest

from src.risk_free import (
    RiskFreeObservation,
    fbil_91d_tbill_to_daily_return,
    align_risk_free_series,
)


def test_exact_numerical_conversion_single_day():
    quoted_yield = 0.0695  # 6.95%
    expected = 0.0695 / 365.0
    result = fbil_91d_tbill_to_daily_return(quoted_yield, 1)
    assert result == pytest.approx(expected, abs=1e-15)


def test_weekend_multi_day_accrual():
    quoted_yield = 0.0700  # 7.00%
    expected = 0.0700 * 3 / 365.0
    result = fbil_91d_tbill_to_daily_return(quoted_yield, 3)
    assert result == pytest.approx(expected, abs=1e-15)


def test_conversion_parameter_validation():
    with pytest.raises(ValueError):
        fbil_91d_tbill_to_daily_return(-0.05, 1)

    with pytest.raises(ValueError):
        fbil_91d_tbill_to_daily_return(0.07, 0)

    with pytest.raises(ValueError):
        fbil_91d_tbill_to_daily_return(0.07, -1)

    with pytest.raises(ValueError):
        fbil_91d_tbill_to_daily_return(float("nan"), 1)


def test_align_risk_free_series_same_day_and_weekend():
    # Fund dates: Friday 2024-01-05, Monday 2024-01-08, Tuesday 2024-01-09
    fund_dates = ["2024-01-05", "2024-01-08", "2024-01-09"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 5),
            quoted_yield=0.0695,
            availability_time=datetime.datetime(2024, 1, 5, 17, 30),
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 8),
            quoted_yield=0.0698,
            availability_time=datetime.datetime(2024, 1, 8, 17, 30),
            revision_seq=1,
        ),
    ]

    analysis_cutoff = "2024-01-15"
    knowledge_cutoff = "2024-01-15T23:59:59"

    aligned = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff,
        knowledge_cutoff,
    )

    assert len(aligned) == 2
    # Interval 1: 2024-01-05 to 2024-01-08 (3 calendar days, yield 0.0695)
    assert aligned[0] == pytest.approx(0.0695 * 3 / 365.0, abs=1e-15)
    # Interval 2: 2024-01-08 to 2024-01-09 (1 calendar day, yield 0.0698)
    assert aligned[1] == pytest.approx(0.0698 * 1 / 365.0, abs=1e-15)


def test_align_risk_free_series_preceding_lookback_holiday():
    # Fund dates: Wednesday 2024-01-10 to Friday 2024-01-12
    # Thursday 2024-01-11 is a market holiday (no quote published)
    fund_dates = ["2024-01-10", "2024-01-11", "2024-01-12"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 10),
            quoted_yield=0.0692,
            availability_time=datetime.datetime(2024, 1, 10, 17, 30),
            revision_seq=1,
        ),
        # 2024-01-11 missing due to holiday
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 12),
            quoted_yield=0.0694,
            availability_time=datetime.datetime(2024, 1, 12, 17, 30),
            revision_seq=1,
        ),
    ]

    aligned = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff="2024-01-15",
        knowledge_cutoff="2024-01-15T23:59:59",
    )

    assert len(aligned) == 2
    # Interval 1 (10 to 11): 1 day, uses 10th yield (0.0692)
    assert aligned[0] == pytest.approx(0.0692 * 1 / 365.0, abs=1e-15)
    # Interval 2 (11 to 12): 1 day, 11th is missing so bridges to 10th yield (0.0692)
    assert aligned[1] == pytest.approx(0.0692 * 1 / 365.0, abs=1e-15)


def test_align_risk_free_series_four_day_lookback_boundary():
    # Gap of exactly 4 calendar days (e.g. 2024-01-01 to 2024-01-05)
    fund_dates = ["2024-01-05", "2024-01-06"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 1),
            quoted_yield=0.0690,
            availability_time=datetime.datetime(2024, 1, 1, 17, 30),
            revision_seq=1,
        ),
    ]

    aligned = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff="2024-01-10",
        knowledge_cutoff="2024-01-10T23:59:59",
        max_lookback_days=4,
    )

    assert len(aligned) == 1
    # 2024-01-05 bridges back 4 days to 2024-01-01
    assert aligned[0] == pytest.approx(0.0690 * 1 / 365.0, abs=1e-15)


def test_align_risk_free_series_rejects_stale_observation():
    # Gap of 5 calendar days > 4 days lookback limit
    fund_dates = ["2024-01-06", "2024-01-07"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 1),
            quoted_yield=0.0690,
            availability_time=datetime.datetime(2024, 1, 1, 17, 30),
            revision_seq=1,
        ),
    ]

    with pytest.raises(ValueError, match="stale"):
        align_risk_free_series(
            fund_dates,
            rf_obs,
            analysis_cutoff="2024-01-10",
            knowledge_cutoff="2024-01-10T23:59:59",
            max_lookback_days=4,
        )


def test_align_risk_free_series_pit_knowledge_cutoff_exclusion():
    # Quote published at 17:30, but knowledge cutoff is 15:30 -> Quote MUST be excluded
    fund_dates = ["2024-01-05", "2024-01-08"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 4),
            quoted_yield=0.0685,
            availability_time=datetime.datetime(2024, 1, 4, 17, 30),
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 5),
            quoted_yield=0.0695,
            availability_time=datetime.datetime(2024, 1, 5, 17, 30),  # Available after 15:30
            revision_seq=1,
        ),
    ]

    # Calculation as-of 2024-01-05 with knowledge cutoff 15:30 IST
    aligned = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff="2024-01-05",
        knowledge_cutoff="2024-01-05T15:30:00",
    )

    # 2024-01-05 rate at 17:30 is excluded by PIT rule; resolves to 2024-01-04 (0.0685)
    assert aligned[0] == pytest.approx(0.0685 * 3 / 365.0, abs=1e-15)


def test_align_risk_free_series_pit_revision_resolution():
    # Day 2024-01-05 has Revision 1 and Revision 2 published
    fund_dates = ["2024-01-05", "2024-01-08"]
    
    rf_obs = [
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 5),
            quoted_yield=0.0690,
            availability_time=datetime.datetime(2024, 1, 5, 17, 30),
            revision_seq=1,
        ),
        RiskFreeObservation(
            effective_date=datetime.date(2024, 1, 5),
            quoted_yield=0.0695,
            availability_time=datetime.datetime(2024, 1, 5, 18, 00),
            revision_seq=2,
        ),
    ]

    # If knowledge cutoff is 17:45 -> Revision 1 is used
    aligned_r1 = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff="2024-01-10",
        knowledge_cutoff="2024-01-05T17:45:00",
    )
    assert aligned_r1[0] == pytest.approx(0.0690 * 3 / 365.0, abs=1e-15)

    # If knowledge cutoff is 18:30 -> Revision 2 is used
    aligned_r2 = align_risk_free_series(
        fund_dates,
        rf_obs,
        analysis_cutoff="2024-01-10",
        knowledge_cutoff="2024-01-05T18:30:00",
    )
    assert aligned_r2[0] == pytest.approx(0.0695 * 3 / 365.0, abs=1e-15)
