"""Unit tests for Brinson-Fachler attribution kernel."""

import pytest
from src.brinson import brinson_fachler_attribution


def test_hand_calculated_brinson_example():
    """
    Test standard 3-segment portfolio vs benchmark.
    Segments: Large Cap, Mid Cap, Small Cap
    Portfolio:
        Large: w = 0.60, R = 0.12
        Mid:   w = 0.30, R = 0.15
        Small: w = 0.10, R = 0.08
    Benchmark:
        Large: w = 0.70, R = 0.10
        Mid:   w = 0.20, R = 0.12
        Small: w = 0.10, R = 0.06

    Benchmark return R_b = 0.70*0.10 + 0.20*0.12 + 0.10*0.06 = 0.070 + 0.024 + 0.006 = 0.100 (10.0%)
    Portfolio return R_p = 0.60*0.12 + 0.30*0.15 + 0.10*0.08 = 0.072 + 0.045 + 0.008 = 0.125 (12.5%)
    Active Return R_A = 0.125 - 0.100 = 0.025 (+2.50%)

    Allocation Effects:
        Large: (0.60 - 0.70) * (0.10 - 0.10) = -0.10 * 0.00 = 0.0000
        Mid:   (0.30 - 0.20) * (0.12 - 0.10) = +0.10 * 0.02 = +0.0020 (+0.20%)
        Small: (0.10 - 0.10) * (0.06 - 0.10) = 0.00 * (-0.04) = 0.0000
        Total Allocation = +0.0020

    Selection Effects:
        Large: 0.70 * (0.12 - 0.10) = 0.70 * 0.02 = +0.0140 (+1.40%)
        Mid:   0.20 * (0.15 - 0.12) = 0.20 * 0.03 = +0.0060 (+0.60%)
        Small: 0.10 * (0.08 - 0.06) = 0.10 * 0.02 = +0.0020 (+0.20%)
        Total Selection = +0.0220

    Interaction Effects:
        Large: (0.60 - 0.70) * (0.12 - 0.10) = -0.10 * 0.02 = -0.0020 (-0.20%)
        Mid:   (0.30 - 0.20) * (0.15 - 0.12) = +0.10 * 0.03 = +0.0030 (+0.30%)
        Small: (0.10 - 0.10) * (0.08 - 0.06) = 0.00 * 0.02 = 0.0000
        Total Interaction = +0.0010

    Reconciliation:
        Alloc + Select + Inter = 0.0020 + 0.0220 + 0.0010 = 0.0250 = R_A
    """
    segments = ["Large Cap", "Mid Cap", "Small Cap"]
    w_p = [0.60, 0.30, 0.10]
    w_b = [0.70, 0.20, 0.10]
    r_p = [0.12, 0.15, 0.08]
    r_b = [0.10, 0.12, 0.06]

    res = brinson_fachler_attribution(segments, w_p, w_b, r_p, r_b)

    assert pytest.approx(res.portfolio_return, 1e-6) == 0.125
    assert pytest.approx(res.benchmark_return, 1e-6) == 0.100
    assert pytest.approx(res.active_return, 1e-6) == 0.025
    assert pytest.approx(res.total_allocation_effect, 1e-6) == 0.0020
    assert pytest.approx(res.total_selection_effect, 1e-6) == 0.0220
    assert pytest.approx(res.total_interaction_effect, 1e-6) == 0.0010
    assert res.is_reconciled
    assert pytest.approx(res.reconciliation_difference, 1e-6) == 0.0


def test_cash_segment_attribution():
    """Test attribution with cash reserve segment."""
    segments = ["Equities", "Cash"]
    w_p = [0.95, 0.05]
    w_b = [1.00, 0.00]
    r_p = [0.10, 0.02]
    r_b = [0.10, 0.02]

    # Portfolio return = 0.95*0.10 + 0.05*0.02 = 0.095 + 0.001 = 0.096
    # Benchmark return = 1.00*0.10 + 0.00*0.02 = 0.100
    # Active return = -0.004 (-0.40% cash drag)
    res = brinson_fachler_attribution(segments, w_p, w_b, r_p, r_b)

    assert pytest.approx(res.active_return, 1e-6) == -0.004
    assert res.is_reconciled
    assert pytest.approx(res.reconciled_total, 1e-6) == -0.004


def test_zero_active_return():
    """Identical weights and returns produce exactly zero active return and effects."""
    segments = ["Large", "Mid"]
    w = [0.8, 0.2]
    r = [0.05, 0.10]
    res = brinson_fachler_attribution(segments, w, w, r, r)

    assert pytest.approx(res.active_return, 1e-6) == 0.0
    assert pytest.approx(res.total_allocation_effect, 1e-6) == 0.0
    assert pytest.approx(res.total_selection_effect, 1e-6) == 0.0
    assert pytest.approx(res.total_interaction_effect, 1e-6) == 0.0
    assert res.is_reconciled


def test_invalid_length_mismatch():
    """Mismatched sequence lengths raise ValueError."""
    with pytest.raises(ValueError, match="identical length"):
        brinson_fachler_attribution(["A", "B"], [0.5], [0.5, 0.5], [0.1, 0.1], [0.1, 0.1])
