import datetime
import pytest

from src.regimes import (
    classify_daily_conditions,
    classify_rolling_market_regimes,
    evaluate_historical_regimes,
)


def test_classify_daily_conditions_empty():
    res = classify_daily_conditions([])
    assert res["total_days"] == 0
    assert res["up_days"] == 0
    assert res["down_days"] == 0


def test_classify_daily_conditions_distribution():
    # 3 up days, 1 down day, 1 flat day
    returns = [0.01, 0.02, -0.01, 0.0, 0.005]
    res = classify_daily_conditions(returns)
    assert res["total_days"] == 5
    assert res["up_days"] == 3
    assert res["down_days"] == 1
    assert res["flat_days"] == 1
    assert res["up_percentage"] == 60.0
    assert res["down_percentage"] == 20.0
    assert res["flat_percentage"] == 20.0


def test_classify_rolling_market_regimes_insufficient():
    res = classify_rolling_market_regimes(["2024-01-01"], [100.0], window_days=21)
    assert res["status"] == "INSUFFICIENT_DATA"
    assert res["total_windows"] == 0


def test_classify_rolling_market_regimes_synthetic():
    dates = [datetime.date(2023, 1, 1) + datetime.timedelta(days=i) for i in range(50)]
    # Trending up then trending down
    values = [100.0 + (i * 0.5) for i in range(25)] + [112.5 - (i * 0.5) for i in range(25)]
    res = classify_rolling_market_regimes(dates, values, window_days=21)
    assert res["status"] == "CALCULATED"
    assert res["total_windows"] == 30
    assert res["advance_windows"] > 0
    assert res["decline_windows"] > 0


def test_evaluate_historical_regimes_mismatched():
    with pytest.raises(ValueError):
        evaluate_historical_regimes(
            fund_dates=["2020-01-01"],
            fund_values=[100.0, 101.0],
            benchmark_dates=["2020-01-01"],
            benchmark_values=[200.0],
        )


def test_evaluate_historical_regimes_calculation():
    dates = ["2020-03-01", "2020-06-01", "2020-11-01"]
    fund_values = [100.0, 120.0, 150.0]
    bench_values = [1000.0, 1150.0, 1400.0]

    res = evaluate_historical_regimes(
        fund_dates=dates,
        fund_values=fund_values,
        benchmark_dates=dates,
        benchmark_values=bench_values,
    )
    assert len(res) == 4
    covid_reg = res[0]
    assert covid_reg["regime_id"] == "REG-01"
    assert covid_reg["trading_days"] == 3
    assert covid_reg["fund_cagr"] is not None
    assert covid_reg["benchmark_cagr"] is not None
    assert covid_reg["excess_return"] is not None

