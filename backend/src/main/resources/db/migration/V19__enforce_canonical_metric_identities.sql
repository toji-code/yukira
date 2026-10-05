-- V18: Enforce Canonical Metric Identities (Resolving V17 and Phase 2H conflicts)
--
-- This migration implements the canonical contract to resolve identity collisions
-- and double-counting risks identified during Phase 2S-B validation.
--
-- CANONICAL MAPPINGS ENFORCED HERE:
--   MKT-01: Beta 3Y (MARKET_SENSITIVITY) - Supersedes REL-01
--   MKT-02: Downside Beta 3Y (MARKET_SENSITIVITY) - Supersedes REL-04
--   MKT-06: Upside Beta 3Y (MARKET_SENSITIVITY) - Supersedes REL-05
--   REL-01: Tracking Error 3Y (BENCHMARK_ALPHA)
--   REL-02: Jensen's Alpha 3Y (BENCHMARK_ALPHA)
--   REL-03: Annualized Mean Active Return 3Y (BENCHMARK_ALPHA)
--   RAT-02: Sortino Ratio 3Y (RISK_ADJUSTED)
--   RAT-03: Treynor Ratio 3Y (RISK_ADJUSTED)
--
-- Deprecated aliases (REL-04, REL-05, REL-06, RAT-05) remain in the table to satisfy 
-- the append-only / no-delete rule, but are formally un-routed by the execution engine.

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
    'Measures the linear sensitivity of fund excess returns to benchmark excess returns over the three-year observation window.',
    'Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)',
    'RATIO',
    'DAILY',
    TRUE,
    TRUE,
    TRUE
),
(
    'MKT-02',
    'Downside Beta 3Y',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures the linear sensitivity of fund returns to benchmark returns, conditioned strictly on days where the benchmark return is negative.',
    'Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'MKT-06',
    'Upside Beta 3Y',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures the linear sensitivity of fund returns to benchmark returns, conditioned strictly on days where the benchmark return is positive.',
    'Cov(R_p, R_b | R_b > 0) / Var(R_b | R_b > 0)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'REL-01',
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
    'REL-02',
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
    'REL-03',
    'Annualized Mean Active Return 3Y',
    'BENCHMARK_ALPHA',
    'BENCHMARK_RELATIVE',
    'Measures the mean daily active return of the fund relative to the benchmark, linearly annualized over 252 trading days.',
    'mean(R_p - R_b) * periods_per_year',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'RAT-02',
    'Sortino Ratio 3Y',
    'RISK_ADJUSTED',
    'RISK_RETURN',
    'Measures excess return per unit of downside risk (semideviation).',
    '(Annualized Return - MAR) / Downside Semideviation',
    'RATIO',
    'DAILY',
    FALSE,
    TRUE,
    TRUE
),
(
    'RAT-03',
    'Treynor Ratio 3Y',
    'RISK_ADJUSTED',
    'RISK_RETURN',
    'Measures excess return per unit of systematic market risk (Beta).',
    'Annualized Excess Return / Beta',
    'RATIO',
    'DAILY',
    TRUE,
    TRUE,
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
