"""Benchmark upside and downside capture calculations."""

import math
from collections.abc import Sequence


def _validate_returns(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> None:
    if len(portfolio_returns) != len(benchmark_returns):
        raise ValueError("portfolio and benchmark returns must have equal length")

    if len(portfolio_returns) == 0:
        raise ValueError("return series cannot be empty")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in portfolio_returns
    ):
        raise ValueError("portfolio returns must be finite numeric values")

    if any(
        not isinstance(value, (int, float)) or not math.isfinite(value)
        for value in benchmark_returns
    ):
        raise ValueError("benchmark returns must be finite numeric values")


def downside_capture(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate downside capture ratio.

    Only periods where the benchmark return is negative are included.

    Formula:
        downside capture =
            portfolio downside return / benchmark downside return * 100

    A value below 100 means the portfolio lost less than the benchmark
    during benchmark-down periods.
    """
    _validate_returns(portfolio_returns, benchmark_returns)

    benchmark_down_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark < 0
    ]

    if not benchmark_down_periods:
        raise ValueError("no benchmark downside periods available")

    portfolio_downside = math.prod(
        1 + portfolio
        for portfolio, _ in benchmark_down_periods
    ) - 1

    benchmark_downside = math.prod(
        1 + benchmark
        for _, benchmark in benchmark_down_periods
    ) - 1

    if benchmark_downside == 0:
        raise ValueError("benchmark downside return cannot be zero")

    return float((portfolio_downside / benchmark_downside) * 100)


def upside_capture(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
) -> float:
    """
    Calculate upside capture ratio.

    Only periods where the benchmark return is positive are included.

    Formula:
        upside capture =
            portfolio upside return / benchmark upside return * 100
    """
    _validate_returns(portfolio_returns, benchmark_returns)

    benchmark_up_periods = [
        (portfolio, benchmark)
        for portfolio, benchmark in zip(
            portfolio_returns,
            benchmark_returns,
        )
        if benchmark > 0
    ]

    if not benchmark_up_periods:
        raise ValueError("no benchmark upside periods available")

    portfolio_upside = math.prod(
        1 + portfolio
        for portfolio, _ in benchmark_up_periods
    ) - 1

    benchmark_upside = math.prod(
        1 + benchmark
        for _, benchmark in benchmark_up_periods
    ) - 1

    if benchmark_upside == 0:
        raise ValueError("benchmark upside return cannot be zero")

    return float((portfolio_upside / benchmark_upside) * 100)


def capture_spread(upside: float, downside: float) -> float:
    """
    Calculate capture spread (MKT-05): Upside Capture - Downside Capture.
    """
    if not math.isfinite(upside) or not math.isfinite(downside):
        raise ValueError("upside and downside capture ratios must be finite numeric values")
    return float(upside - downside)


def compute_capture_metrics(
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    min_up_days: int = 150,
    min_down_days: int = 100,
) -> dict:
    """
    Compute full capture ratio analysis according to MKT-03, MKT-04, and MKT-05:
      - Upside Capture Ratio (>= 150 up days required)
      - Downside Capture Ratio (>= 100 down days required)
      - Capture Spread (UC - DC)
      - Cumulative participation returns for up and down market subsets
      - Strict handling of flat days (Rb = 0) and zero denominators
    """
    _validate_returns(portfolio_returns, benchmark_returns)

    total_paired = len(portfolio_returns)
    up_pairs = [
        (p, b) for p, b in zip(portfolio_returns, benchmark_returns) if b > 0
    ]
    down_pairs = [
        (p, b) for p, b in zip(portfolio_returns, benchmark_returns) if b < 0
    ]
    flat_pairs = [
        (p, b) for p, b in zip(portfolio_returns, benchmark_returns) if b == 0
    ]

    up_count = len(up_pairs)
    down_count = len(down_pairs)
    flat_count = len(flat_pairs)

    # 1. Upside Evaluation (MKT-03)
    p_up_cum = None
    b_up_cum = None
    uc_val = None
    uc_status = "INSUFFICIENT_DATA"
    uc_err = None

    if up_count > 0:
        p_up_cum = math.prod(1 + p for p, _ in up_pairs) - 1
        b_up_cum = math.prod(1 + b for _, b in up_pairs) - 1

    if up_count < min_up_days:
        uc_err = f"Insufficient positive benchmark days: {up_count} provided, minimum {min_up_days} required."
    elif b_up_cum == 0:
        uc_status = "INVALID_DATA"
        uc_err = "Cumulative benchmark upside return is zero."
    else:
        uc_val = float((p_up_cum / b_up_cum) * 100)
        uc_status = "CALCULATED"

    # 2. Downside Evaluation (MKT-04)
    p_down_cum = None
    b_down_cum = None
    dc_val = None
    dc_status = "INSUFFICIENT_DATA"
    dc_err = None
    inverse_gain = False

    if down_count > 0:
        p_down_cum = math.prod(1 + p for p, _ in down_pairs) - 1
        b_down_cum = math.prod(1 + b for _, b in down_pairs) - 1
        if p_down_cum > 0:
            inverse_gain = True

    if down_count < min_down_days:
        dc_err = f"Insufficient negative benchmark days: {down_count} provided, minimum {min_down_days} required."
    elif b_down_cum == 0:
        dc_status = "INVALID_DATA"
        dc_err = "Cumulative benchmark downside return is zero."
    else:
        dc_val = float((p_down_cum / b_down_cum) * 100)
        dc_status = "CALCULATED"

    # 3. Capture Spread (MKT-05)
    spread_val = None
    spread_status = "INSUFFICIENT_DATA"
    spread_err = None

    if uc_status == "CALCULATED" and dc_status == "CALCULATED" and uc_val is not None and dc_val is not None:
        spread_val = float(uc_val - dc_val)
        spread_status = "CALCULATED"
    else:
        spread_err = "Both upside and downside capture ratios must be calculated to derive capture spread."

    return {
        "total_paired_days": total_paired,
        "up_days_count": up_count,
        "down_days_count": down_count,
        "flat_days_count": flat_count,
        "min_up_days_required": min_up_days,
        "min_down_days_required": min_down_days,
        "is_up_sufficient": up_count >= min_up_days,
        "is_down_sufficient": down_count >= min_down_days,
        "fund_up_cumulative": p_up_cum,
        "bench_up_cumulative": b_up_cum,
        "upside_capture": uc_val,
        "upside_status": uc_status,
        "upside_error": uc_err,
        "fund_down_cumulative": p_down_cum,
        "bench_down_cumulative": b_down_cum,
        "downside_capture": dc_val,
        "downside_status": dc_status,
        "downside_error": dc_err,
        "inverse_capture_gain": inverse_gain,
        "capture_spread": spread_val,
        "spread_status": spread_status,
        "spread_error": spread_err,
    }