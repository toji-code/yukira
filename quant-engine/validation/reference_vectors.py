"""Deterministic reference vectors for Phase 2S-A validation.

Each representative metric includes:
1. Normal Case: Standard series with verified mathematical outputs.
2. Boundary Case: Edge threshold (e.g. zero drawdown, minimal period, circuit bounds).
3. Insufficient Case: Observations below required minimum (asserting controlled failure).
4. Degenerate Case: Structurally invalid data (asserting explicit rejection).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ReferenceVectorSuite:
    metric_code: str
    normal_case: dict[str, Any]
    boundary_case: dict[str, Any]
    insufficient_case: dict[str, Any]
    degenerate_case: dict[str, Any]


# 1. RET-03 (3Y CAGR)
RET03_VECTORS = ReferenceVectorSuite(
    metric_code="RET-03",
    normal_case={
        "start_value": 1000.0,
        "end_value": 1864.38865,
        "elapsed_calendar_days": 1095,  # 3 Julian years = 1095 days (1095 / 365.25 = 2.9979466)
        "description": "Standard 3-year compounding growth",
        # (1864.38865 / 1000) ** (365.25 / 1095) - 1.0 = 0.23075253 (approx)
    },
    boundary_case={
        "start_value": 100.0,
        "end_value": 100.0,
        "elapsed_calendar_days": 365.25,
        "expected_cagr": 0.0,
        "description": "Flat performance over exact Julian year",
    },
    insufficient_case={
        "start_value": 100.0,
        "end_value": 110.0,
        "elapsed_calendar_days": 0,
        "description": "Zero elapsed calendar days (division by zero guard)",
    },
    degenerate_case={
        "start_value": -100.0,
        "end_value": 110.0,
        "elapsed_calendar_days": 365,
        "description": "Non-positive NAV start value",
    },
)

# 2. RSK-01 (Annualized Volatility)
RSK01_VECTORS = ReferenceVectorSuite(
    metric_code="RSK-01",
    normal_case={
        # Hand-verifiable 5-point return vector: mean = 0.01
        # returns: [0.02, -0.01, 0.03, 0.00, 0.01]
        # deviations: [0.01, -0.02, 0.02, -0.01, 0.00]
        # squared deviations: [0.0001, 0.0004, 0.0004, 0.0001, 0.0000] -> sum = 0.0010
        # sample var = 0.0010 / (5 - 1) = 0.00025
        # sample std = sqrt(0.00025) = 0.0158113883
        # annualized (periods_per_year=252) = 0.0158113883 * sqrt(252) = 0.2510378457
        "returns": [0.02, -0.01, 0.03, 0.00, 0.01],
        "periods_per_year": 252.0,
        "expected_volatility": 0.015811388300841896 * (252.0 ** 0.5),
        "description": "5-point balanced return series",
    },
    boundary_case={
        # Zero variance: constant returns
        "returns": [0.01, 0.01, 0.01, 0.01],
        "periods_per_year": 252.0,
        "expected_volatility": 0.0,
        "description": "Zero volatility constant return series",
    },
    insufficient_case={
        "returns": [0.05],  # Single return point, N < 2
        "periods_per_year": 252.0,
        "description": "Fewer than 2 observations for sample variance",
    },
    degenerate_case={
        "returns": [0.01, float("nan"), 0.03],
        "periods_per_year": 252.0,
        "description": "Non-finite NaN observation in return series",
    },
)

# 3. RSK-03 (Maximum Drawdown)
RSK03_VECTORS = ReferenceVectorSuite(
    metric_code="RSK-03",
    normal_case={
        # Peak at 120, trough at 90 -> dd = (90 - 120) / 120 = -0.25 (-25%)
        "nav_series": [100.0, 110.0, 120.0, 105.0, 90.0, 115.0, 125.0],
        "expected_max_drawdown": -0.25,
        "description": "Standard peak-to-trough decline with subsequent recovery",
    },
    boundary_case={
        # Monotonically increasing: zero drawdown
        "nav_series": [100.0, 102.0, 105.0, 110.0, 120.0],
        "expected_max_drawdown": 0.0,
        "description": "Monotonically increasing NAV series (zero drawdown)",
    },
    insufficient_case={
        "nav_series": [],
        "description": "Empty NAV series",
    },
    degenerate_case={
        "nav_series": [100.0, -10.0, 90.0],
        "description": "Negative NAV observation",
    },
)

# 4. RSK-06 (Historical VaR 95%)
RSK06_VECTORS = ReferenceVectorSuite(
    metric_code="RSK-06",
    normal_case={
        # 21 sorted returns from -0.10 to +0.10 (step 0.01)
        # alpha = 0.05 * 20 = 1.0 -> index 1 -> return is -0.09
        # VaR = -(-0.09) = +0.09
        "returns": [round(-0.10 + i * 0.01, 4) for i in range(21)],
        "confidence_level": 0.95,
        "expected_var": 0.09,
        "description": "Uniform 21-point discrete return distribution",
    },
    boundary_case={
        # All returns positive: VaR is negative (indicating gain at quantile)
        "returns": [0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.10],
        "confidence_level": 0.95,
        "description": "All positive returns tail boundary",
    },
    insufficient_case={
        "returns": [0.01],
        "confidence_level": 0.95,
        "description": "Single observation (insufficient for quantile)",
    },
    degenerate_case={
        "returns": [0.01, 0.02, float("inf")],
        "confidence_level": 0.95,
        "description": "Infinite return value",
    },
)

# 5. RAT-01 (Sharpe Ratio)
RAT01_VECTORS = ReferenceVectorSuite(
    metric_code="RAT-01",
    normal_case={
        "returns": [0.02, -0.01, 0.03, 0.00, 0.01],
        "risk_free_rate": 0.0002,  # Scalar daily risk free rate
        # excess: [0.0198, -0.0102, 0.0298, -0.0002, 0.0098]
        # mean excess: 0.0098
        # std excess: same as returns std = 0.0158113883
        # Sharpe = (0.0098 / 0.0158113883) * sqrt(252) = 0.6198064 * 15.874507866 = 9.839121
        "periods_per_year": 252.0,
        "description": "Positive excess return Sharpe ratio test",
    },
    boundary_case={
        # Excess returns zero on average
        "returns": [0.0002, 0.0002, 0.0002, 0.0002],
        "risk_free_rate": 0.0002,
        "periods_per_year": 252.0,
        "description": "Zero variance excess return (undefined Sharpe ratio)",
    },
    insufficient_case={
        "returns": [0.01],
        "risk_free_rate": 0.0002,
        "periods_per_year": 252.0,
        "description": "Single observation Sharpe ratio",
    },
    degenerate_case={
        "returns": [0.01, 0.02],
        "risk_free_rate": [0.0002],  # Length mismatch with returns (2 vs 1)
        "periods_per_year": 252.0,
        "description": "Length mismatch between returns and risk-free series",
    },
)

# 6. REL-01 (Beta)
REL01_VECTORS = ReferenceVectorSuite(
    metric_code="REL-01",
    normal_case={
        # Benchmark excess: [-0.02, -0.01, 0.00, 0.01, 0.02] -> var_b = 0.00025
        # Portfolio excess = 1.2 * benchmark excess: [-0.024, -0.012, 0.000, 0.012, 0.024]
        # Expected beta = 1.2 exactly
        "portfolio_returns": [-0.024, -0.012, 0.000, 0.012, 0.024],
        "benchmark_returns": [-0.020, -0.010, 0.000, 0.010, 0.020],
        "risk_free_rates": None,
        "expected_beta": 1.2,
        "description": "Proportional 1.2x market sensitivity",
    },
    boundary_case={
        # Uncorrelated orthogonal returns: Beta = 0.0
        "portfolio_returns": [0.01, -0.01, 0.01, -0.01],
        "benchmark_returns": [0.01, 0.01, -0.01, -0.01],
        "risk_free_rates": None,
        "expected_beta": 0.0,
        "description": "Orthogonal uncorrelated series (zero beta)",
    },
    insufficient_case={
        "portfolio_returns": [0.01],
        "benchmark_returns": [0.01],
        "risk_free_rates": None,
        "description": "Single paired observation",
    },
    degenerate_case={
        # Benchmark variance is zero
        "portfolio_returns": [0.01, 0.02, 0.03],
        "benchmark_returns": [0.01, 0.01, 0.01],
        "risk_free_rates": None,
        "description": "Zero benchmark variance (undefined Beta)",
    },
)

ALL_REFERENCE_VECTORS = {
    "RET-03": RET03_VECTORS,
    "RSK-01": RSK01_VECTORS,
    "RSK-03": RSK03_VECTORS,
    "RSK-06": RSK06_VECTORS,
    "RAT-01": RAT01_VECTORS,
    "REL-01": REL01_VECTORS,
}
