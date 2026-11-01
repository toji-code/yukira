-- Phase 2R / V1-M3: Seed RAT-01 (Sharpe Ratio 3Y) and RET-07 (3Y Active Return) Metric Definitions

INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'RAT-01',
    'Sharpe Ratio (3Y)',
    'RISK_ADJUSTED',
    'RISK_RETURN',
    'Quantifies annualized excess return earned per unit of total return volatility above the risk-free cash rate (FBIL 91-Day T-Bill).',
    '(Fund_3Y_CAGR - Rf_Annualized) / Volatility_3Y',
    'RATIO',
    'DAILY',
    FALSE,
    TRUE,
    TRUE
),
(
    'RET-07',
    '3-Year Annualized Active Return',
    'RETURNS',
    'PERFORMANCE',
    'Measures annualized excess compound return generated above the primary market benchmark over a 36-month evaluation horizon.',
    'Fund_3Y_CAGR - Benchmark_3Y_CAGR',
    'PERCENTAGE',
    'DISCRETE_PERIOD',
    TRUE,
    FALSE,
    TRUE
)
ON CONFLICT (metric_code) DO NOTHING;
