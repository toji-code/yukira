import pytest


def test_compute_rolling_windows_unsupported_period():
    from src.rolling import compute_rolling_windows

    with pytest.raises(ValueError, match="Unsupported period"):
        compute_rolling_windows(
            dates=["2024-01-01"],
            values=[100.0],
            period_type="10Y",
        )


def test_rolling_windows_insufficient_data():
    from src.rolling import compute_rolling_windows

    dates = ["2024-01-01", "2024-01-02"]
    values = [100.0, 101.0]

    res = compute_rolling_windows(dates, values, period_type="3Y")
    assert res["status"] == "INSUFFICIENT_DATA"
    assert res["metric_code"] == "RET-05"
    assert res["windows_calculated"] == 0


def test_rolling_excess_returns():
    from src.rolling import compute_rolling_excess_returns

    dates = ["2024-01-01", "2024-01-02"]
    fund_navs = [100.0, 101.0]
    bench_navs = [200.0, 202.0]

    res = compute_rolling_excess_returns(
        fund_dates=dates,
        fund_navs=fund_navs,
        bench_dates=dates,
        bench_navs=bench_navs,
        period_type="3Y",
    )
    assert res["status"] == "INSUFFICIENT_DATA"
    assert res["metric_code"] == "RET-06"


def test_rolling_cagr_calculation():
    from src.rolling import compute_rolling_cagr

    dates = ["2021-01-01", "2024-01-01"]
    navs = [100.0, 200.0]

    res = compute_rolling_cagr(dates, navs, window_years=3)
    assert res["status"] == "INSUFFICIENT_DATA"
    assert res["total_windows"] == 0


def test_compute_rolling_cagrs_multi_window():
    from src.rolling import compute_rolling_cagrs

    dates = ["2020-01-01", "2021-01-01", "2022-01-01", "2023-01-01"]
    values = [100.0, 110.0, 121.0, 133.10]

    # 1Y rolling windows
    wins_1y = compute_rolling_cagrs(dates, values, window_years=1)
    assert len(wins_1y) == 3
    for w in wins_1y:
        assert w["cagr"] == pytest.approx(0.10, rel=1e-2)

    # 2Y rolling windows
    wins_2y = compute_rolling_cagrs(dates, values, window_years=2)
    assert len(wins_2y) == 2
    for w in wins_2y:
        assert w["cagr"] == pytest.approx(0.10, rel=1e-2)

    # 3Y rolling window
    wins_3y = compute_rolling_cagrs(dates, values, window_years=3)
    assert len(wins_3y) == 1
    assert wins_3y[0]["start_date"] == "2020-01-01"
    assert wins_3y[0]["end_date"] == "2023-01-01"
    assert wins_3y[0]["cagr"] == pytest.approx(0.10, rel=1e-2)


def test_compute_rolling_cagrs_lookback_bridging():
    from src.rolling import compute_rolling_cagrs

    # Saturday/Sunday missing on exact 1Y lookback: Jan 15 2023 is Sunday, nearest preceding is Friday Jan 13
    dates = ["2022-01-13", "2022-01-14", "2023-01-16"]
    values = [100.0, 102.0, 115.0]

    # Looking back 1Y from 2023-01-16 targets 2022-01-16; lookback up to 4 days finds 2022-01-14
    wins = compute_rolling_cagrs(dates, values, window_years=1, max_lookback_days=4)
    assert len(wins) == 1
    assert wins[0]["start_date"] == "2022-01-14"
    assert wins[0]["end_date"] == "2023-01-16"


def test_rolling_return_distribution_stats():
    from src.rolling import rolling_return_distribution

    cagrs = [0.10, 0.12, 0.14, 0.16, 0.20]
    dist = rolling_return_distribution(cagrs)

    assert dist["count"] == 5
    assert dist["mean"] == pytest.approx(0.144)
    assert dist["median"] == pytest.approx(0.14)
    assert dist["min"] == pytest.approx(0.10)
    assert dist["max"] == pytest.approx(0.20)
    assert dist["p25"] == pytest.approx(0.12)
    assert dist["p75"] == pytest.approx(0.16)
    assert dist["std_dev"] > 0.0

    # Empty handling
    empty_dist = rolling_return_distribution([])
    assert empty_dist["count"] == 0
    assert empty_dist["mean"] is None


def test_rolling_outperformance_strict_inequality():
    from src.rolling import rolling_outperformance

    # Outperforming in 2 out of 3, tie in 1
    # §RET-06 specifies: no ties count as outperformance
    fund_wins = [
        {"start_date": "2021-01-01", "end_date": "2022-01-01", "cagr": 0.15},
        {"start_date": "2021-01-02", "end_date": "2022-01-02", "cagr": 0.10},  # Tie
        {"start_date": "2021-01-03", "end_date": "2022-01-03", "cagr": 0.05},  # Underperform
        {"start_date": "2021-01-04", "end_date": "2022-01-04", "cagr": 0.25},  # Outperform
    ]
    bench_wins = [
        {"start_date": "2021-01-01", "end_date": "2022-01-01", "cagr": 0.12},
        {"start_date": "2021-01-02", "end_date": "2022-01-02", "cagr": 0.10},  # Tie
        {"start_date": "2021-01-03", "end_date": "2022-01-03", "cagr": 0.08},
        {"start_date": "2021-01-04", "end_date": "2022-01-04", "cagr": 0.20},
    ]

    outperf = rolling_outperformance(fund_wins, bench_wins)
    assert outperf["paired_windows"] == 4
    # Ties do NOT count as outperformance: exactly 2 windows outperformed (0.15 > 0.12 and 0.25 > 0.20)
    assert outperf["outperforming_windows"] == 2
    assert outperf["underperforming_windows"] == 2
    assert outperf["outperformance_percentage"] == pytest.approx(50.0)