-- V17: Re-apply the authoritative metric registry identities to the live database.
--
-- PROBLEM OBSERVED IN THE LIVE DATABASE (2026-10-01 audit):
--   The applied V16 body did not land the intended identities. The registry held a
--   one-position identity shift inside the benchmark/alpha family:
--       REL-01 = "Tracking Error 3Y"  (must be Beta 3Y)
--       REL-02 = "Jensen's Alpha 3Y"  (must be Tracking Error 3Y)
--       REL-03 = absent                (must be Jensen's Alpha 3Y)
--       REL-06 = absent                (must be Annualized Mean Active Return 3Y)
--
-- AUTHORITATIVE CONTRACT (source-of-truth precedence: explicit project owner directive,
-- reinforced by CR-04 in docs/research/PHASE_2S_B_SCOPE_LOCK.md = RESOLVED, the frozen
-- backend METRIC_METADATA map in AnalysisService, the quant-engine dispatcher, and
-- README.md). Note: phase2h_quantitative_methodology.md lines 111-112 still carry the
-- superseded pre-CR-04 numbering for REL-01/REL-02; that document is FROZEN and is NOT
-- edited here. This migration restates identities only, it does not alter, approve or
-- validate any methodology.
--
--   REL-01 = Beta                              MKT-01 = Beta (alias of REL-01)
--   REL-02 = Tracking Error                    REL-03 = Jensen's Alpha
--   REL-04 = Downside Beta                     REL-05 = Upside Beta
--   REL-06 = Annualized Mean Active Return     MKT-02 = Downside Beta (alias of REL-04)
--                                             RAT-04 = Information Ratio
--                                             MKT-03 / MKT-04 / MKT-05 = Capture metrics
--
-- GOVERNANCE CONSTRAINTS:
--   * Append-only. No DELETE, TRUNCATE, DROP, or ALTER of any table.
--   * Idempotent: INSERT ... ON CONFLICT (metric_code) DO UPDATE. Re-running is a no-op.
--   * Preserves valid existing definitions. Only the four drifted/absent rows below are
--     written. MKT-01, MKT-02, MKT-03, MKT-04, MKT-05, RAT-04, REL-04 and REL-05 already
--     satisfy the contract and are deliberately left untouched.
--   * Units and analytical_dimension are deterministic facts of the quantitative kernel
--     (quant-engine/src/api/dispatcher.py): Beta/Downside Beta/Upside Beta are RATIO;
--     Tracking Error, Jensen's Alpha and Mean Active Return are PERCENTAGE.
--
-- 1. Reconcile the drifted and missing benchmark/alpha identities.
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'REL-01',
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
    'REL-06',
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

-- 2. Non-destructive contract guard. Verifies the complete ten-row authoritative contract
--    is present and correctly typed after the reconciliation above, including the rows this
--    migration deliberately does not write. Aborts the migration (rolling back section 1)
--    rather than leaving a silently incomplete registry.
DO $$
DECLARE
    v_violations TEXT;
BEGIN
    SELECT string_agg(format('%s=%s/%s', expected.metric_code, actual.metric_name, actual.units), '; ')
    INTO v_violations
    FROM (VALUES
        ('REL-01', 'MARKET_SENSITIVITY', 'RATIO'),
        ('REL-02', 'BENCHMARK_ALPHA',    'PERCENTAGE'),
        ('REL-03', 'BENCHMARK_ALPHA',    'PERCENTAGE'),
        ('REL-04', 'MARKET_SENSITIVITY', 'RATIO'),
        ('REL-05', 'MARKET_SENSITIVITY', 'RATIO'),
        ('REL-06', 'BENCHMARK_ALPHA',    'PERCENTAGE'),
        ('MKT-01', 'MARKET_SENSITIVITY', 'RATIO'),
        ('MKT-02', 'MARKET_SENSITIVITY', 'RATIO'),
        ('MKT-03', 'MARKET_SENSITIVITY', 'PERCENTAGE'),
        ('MKT-04', 'MARKET_SENSITIVITY', 'PERCENTAGE'),
        ('MKT-05', 'MARKET_SENSITIVITY', 'PERCENTAGE_POINTS'),
        ('RAT-04', 'RISK_ADJUSTED',      'RATIO')
    ) AS expected(metric_code, analytical_dimension, units)
    LEFT JOIN metric_definition actual
        ON actual.metric_code = expected.metric_code
       AND actual.analytical_dimension = expected.analytical_dimension
       AND actual.units = expected.units
    WHERE actual.id IS NULL;

    IF v_violations IS NOT NULL THEN
        RAISE EXCEPTION
            'V17 metric registry contract violated; expected (metric_name/units) rows not satisfied for: %',
            v_violations;
    END IF;
END $$;
