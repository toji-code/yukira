-- Restore metric_definition identities to the verified quant metric contract.
-- Earlier backend slices reused MKT/REL codes for implementation panels; this
-- corrective migration restores the authoritative registry names without
-- deleting historical calculation runs or metric_result rows.

INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'MKT-01',
    'Beta 3Y',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures co-movement sensitivity of the fund relative to its benchmark over the three-year observation window.',
    'Cov(R_p, R_b) / Var(R_b)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'MKT-02',
    'Downside Beta',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures sensitivity during benchmark down-market regimes.',
    'Cov(R_p, R_b | downside regime) / Var(R_b | downside regime)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'REL-01',
    'Beta 3Y',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures co-movement sensitivity of the fund relative to its benchmark over the three-year observation window.',
    'Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)',
    'RATIO',
    'DAILY',
    TRUE,
    TRUE,
    TRUE
),
(
    'REL-02',
    'Tracking Error 3Y',
    'BENCHMARK_ALPHA',
    'BENCHMARK_RELATIVE',
    'Measures annualized standard deviation of active returns relative to the benchmark.',
    'stdev(R_p - R_b) * sqrt(252)',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'REL-03',
    'Jensen''s Alpha 3Y',
    'BENCHMARK_ALPHA',
    'BENCHMARK_RELATIVE',
    'Measures annualized intercept over a single-index CAPM relationship.',
    'R_p - [R_f + beta * (R_m - R_f)]',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    TRUE,
    TRUE
),
(
    'RAT-04',
    'Information Ratio 3Y',
    'RISK_ADJUSTED',
    'BENCHMARK_RELATIVE',
    'Measures annualized active return generated per unit of tracking error.',
    'Annualized active return / Annualized tracking error',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
)
ON CONFLICT (metric_code) DO UPDATE SET
    metric_name = EXCLUDED.metric_name,
    analytical_dimension = EXCLUDED.analytical_dimension,
    metric_category = EXCLUDED.metric_category,
    purpose = EXCLUDED.purpose,
    formula_display = EXCLUDED.formula_display,
    units = EXCLUDED.units,
    default_frequency = EXCLUDED.default_frequency,
    benchmark_required = EXCLUDED.benchmark_required,
    risk_free_required = EXCLUDED.risk_free_required,
    point_in_time_required = EXCLUDED.point_in_time_required;
