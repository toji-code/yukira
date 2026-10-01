-- Benchmark Beta Dynamics: Seed for REL-01 (Standard Beta 3Y), REL-04 (Downside Beta 3Y), and REL-05 (Upside Beta 3Y) Metric Definitions and Candidate Methodology Versions

-- 1. Seed Metric Definitions
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'REL-01',
    'Standard Beta (3Y Excess-Return OLS)',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures the linear sensitivity of portfolio excess returns to benchmark excess returns over 36 calendar months via a single-index OLS regression with intercept.',
    'Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)',
    'RATIO',
    'DAILY',
    TRUE,
    TRUE,
    TRUE
),
(
    'REL-04',
    'Downside Beta (3Y)',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures portfolio co-movement with the benchmark conditioned strictly on benchmark down-days (R_b < 0) over 36 calendar months, isolating sell-off sensitivity.',
    'Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'REL-05',
    'Upside Beta (3Y)',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures portfolio co-movement with the benchmark conditioned strictly on benchmark up-days (R_b > 0) over 36 calendar months, isolating advance participation.',
    'Cov(R_p, R_b | R_b > 0) / Var(R_b | R_b > 0)',
    'RATIO',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
)
ON CONFLICT (metric_code) DO UPDATE SET
    metric_name = EXCLUDED.metric_name,
    purpose = EXCLUDED.purpose,
    formula_display = EXCLUDED.formula_display,
    units = EXCLUDED.units,
    analytical_dimension = EXCLUDED.analytical_dimension,
    metric_category = EXCLUDED.metric_category;

-- 2. Seed Initial Candidate Methodology Versions
DO $$
DECLARE
    v_metric_id_rel01 BIGINT;
    v_metric_id_rel04 BIGINT;
    v_metric_id_rel05 BIGINT;
BEGIN
    SELECT id INTO v_metric_id_rel01 FROM metric_definition WHERE metric_code = 'REL-01';
    SELECT id INTO v_metric_id_rel04 FROM metric_definition WHERE metric_code = 'REL-04';
    SELECT id INTO v_metric_id_rel05 FROM metric_definition WHERE metric_code = 'REL-05';

    -- REL-01 Methodology Version (spec: Phase 2N M2N-06)
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'REL_01_3Y_BETA' AND version_tag = 'CANDIDATE_V1') THEN
        INSERT INTO methodology_version (
            metric_definition_id, methodology_code, version_tag,
            approval_status, lifecycle_status, validation_status,
            git_commit_hash, parameter_configuration,
            mathematical_definition, formula_reference,
            required_inputs, frequency_assumptions, lookback_rule,
            observation_date_semantics, information_set_requirement,
            annualization_convention, denominator_convention,
            missing_data_rule, insufficient_history_rule, invalid_data_rule,
            quality_prerequisites, known_limitations, is_locked
        ) VALUES (
            v_metric_id_rel01, 'REL_01_3Y_BETA', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_paired_observations": 700, "lookback_years": 3}'::jsonb,
            'beta = Cov(R_p,t - R_f,t, R_b,t - R_f,t) / Var(R_b,t - R_f,t) from a single-index OLS regression with intercept over synchronous paired trading days',
            'Phase 2N M2N-06 Excess-Return Single-Index OLS',
            'Daily NAV series, Benchmark TRI series, and FBIL 91-Day T-Bill risk-free series over 36 months',
            'Daily paired observations', '36 calendar months analytical window',
            'Synchronously paired trading days on identical calendar dates',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE', 'SAMPLE_COVARIANCE_N_MINUS_1',
            'Paired synchronous exclusion without calendar date substitution or forward-fill.',
            '< 700 paired trading days returns INSUFFICIENT_DATA with a NULL metric value.',
            'Zero benchmark excess-return variance halts execution with division-by-zero error.',
            'Synchronous daily dates without data conflicts.',
            'Specification APPROVED under Phase 2N M2N-06; ledger implementation remains UNVALIDATED across market cycles. Beta assumes stationary linear covariance and changes regime during market stress.',
            FALSE
        );
    END IF;

    -- REL-04 Methodology Version (spec: Phase 2N M2N-07)
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'REL_04_3Y_DOWNSIDE_BETA' AND version_tag = 'CANDIDATE_V1') THEN
        INSERT INTO methodology_version (
            metric_definition_id, methodology_code, version_tag,
            approval_status, lifecycle_status, validation_status,
            git_commit_hash, parameter_configuration,
            mathematical_definition, formula_reference,
            required_inputs, frequency_assumptions, lookback_rule,
            observation_date_semantics, information_set_requirement,
            annualization_convention, denominator_convention,
            missing_data_rule, insufficient_history_rule, invalid_data_rule,
            quality_prerequisites, known_limitations, is_locked
        ) VALUES (
            v_metric_id_rel04, 'REL_04_3Y_DOWNSIDE_BETA', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_downside_observations": 100, "lookback_years": 3}'::jsonb,
            'beta_down = Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0) over the down-day subsample of synchronous paired trading days',
            'Phase 2N M2N-07 Downside Beta Specification',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months analytical window',
            'Synchronously paired trading days on identical calendar dates',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE', 'SAMPLE_COVARIANCE_N_MINUS_1',
            'Paired synchronous exclusion without calendar date substitution or forward-fill.',
            '< 100 benchmark down-days (R_b < 0) returns INSUFFICIENT_DATA with a NULL metric value.',
            'Zero down-day benchmark variance halts execution with division-by-zero error.',
            'Synchronous daily dates without data conflicts.',
            'Specification APPROVED under Phase 2N M2N-07; ledger implementation remains UNVALIDATED across market cycles. Conditioned subsample reduces effective sample size and increases estimation error.',
            FALSE
        );
    END IF;

    -- REL-05 Methodology Version (no frozen specification; candidate symmetrical extension of M2N-07)
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'REL_05_3Y_UPSIDE_BETA' AND version_tag = 'CANDIDATE_V1') THEN
        INSERT INTO methodology_version (
            metric_definition_id, methodology_code, version_tag,
            approval_status, lifecycle_status, validation_status,
            git_commit_hash, parameter_configuration,
            mathematical_definition, formula_reference,
            required_inputs, frequency_assumptions, lookback_rule,
            observation_date_semantics, information_set_requirement,
            annualization_convention, denominator_convention,
            missing_data_rule, insufficient_history_rule, invalid_data_rule,
            quality_prerequisites, known_limitations, is_locked
        ) VALUES (
            v_metric_id_rel05, 'REL_05_3Y_UPSIDE_BETA', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_upside_observations": 150, "lookback_years": 3}'::jsonb,
            'beta_up = Cov(R_p, R_b | R_b > 0) / Var(R_b | R_b > 0) over the up-day subsample of synchronous paired trading days',
            'Candidate symmetrical extension of Phase 2N M2N-07 conditioned on R_b > 0',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months analytical window',
            'Synchronously paired trading days on identical calendar dates',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE', 'SAMPLE_COVARIANCE_N_MINUS_1',
            'Paired synchronous exclusion without calendar date substitution or forward-fill.',
            '< 150 benchmark up-days (R_b > 0) returns INSUFFICIENT_DATA with a NULL metric value.',
            'Zero up-day benchmark variance halts execution with division-by-zero error.',
            'Synchronous daily dates without data conflicts.',
            'No frozen methodology specification exists for upside beta. Candidate methodology pending governance review; not validated for production decision support. Conditioned subsample reduces effective sample size and increases estimation error.',
            FALSE
        );
    END IF;
END $$;
