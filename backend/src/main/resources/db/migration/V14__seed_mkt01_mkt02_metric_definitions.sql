-- Phase 2T: Seed for MKT-01 (Tracking Error 3Y) and MKT-02 (Information Ratio 3Y) Metric Definitions and Candidate Methodology Versions

-- 1. Seed Metric Definitions
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'MKT-01',
    'Tracking Error (3Y Annualized)',
    'MARKET_SENSITIVITY',
    'BENCHMARK_RELATIVE',
    'Measures active return volatility, quantifying the dispersion of daily excess returns relative to the benchmark over 36 calendar months.',
    'sqrt(1/(N-1) * sum((e_t - mean(e))^2)) * sqrt(252)',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'MKT-02',
    'Information Ratio (3Y Annualized)',
    'MARKET_SENSITIVITY',
    'RISK_ADJUSTED',
    'Measures the annualized excess return generated per unit of active risk (Tracking Error) relative to the primary benchmark over 36 calendar months.',
    '(mean(e_t) * 252) / (Tracking_Error_Annualized)',
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
    v_metric_id_01 BIGINT;
    v_metric_id_02 BIGINT;
BEGIN
    SELECT id INTO v_metric_id_01 FROM metric_definition WHERE metric_code = 'MKT-01';
    SELECT id INTO v_metric_id_02 FROM metric_definition WHERE metric_code = 'MKT-02';

    -- MKT-01 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'MKT_01_3Y_TRACKING_ERROR' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_01, 'MKT_01_3Y_TRACKING_ERROR', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_paired_observations": 700}'::jsonb,
            'TE = sqrt(1/(N-1) * sum_{t=1}^N (e_t - bar{e})^2) * sqrt(252) where e_t = R_p,t - R_b,t',
            'Annualized tracking error per Bacon (2008)',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months candidate analytical window',
            'Synchronously paired trading days on identical calendar dates',
            'Strictly availability_time <= knowledgeCutoffTime',
            'SQRT_252', 'SAMPLE_VARIANCE_N_MINUS_1',
            'Paired synchronous exclusion without calendar date substitution.',
            '< 700 paired trading days returns insufficient data. Metric value is NULL.',
            'Non-finite return values halt execution.',
            'Synchronous daily dates without data conflicts.',
            'In active equity, high tracking error can reflect either unconstrained manager conviction or undisciplined factor drift. Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;

    -- MKT-02 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'MKT_02_3Y_INFORMATION_RATIO' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_02, 'MKT_02_3Y_INFORMATION_RATIO', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_paired_observations": 700}'::jsonb,
            'IR = (bar{e} / s_e) * sqrt(252) = (bar{e} * 252) / (s_e * sqrt(252))',
            'Annualized Information Ratio per Grinold & Kahn (1999) / Bacon (2008)',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months candidate analytical window',
            'Synchronously paired trading days on identical calendar dates',
            'Strictly availability_time <= knowledgeCutoffTime',
            'SQRT_252_ON_DAILY_MEAN_OVER_TE', 'SAMPLE_TRACKING_ERROR_N_MINUS_1',
            'Paired synchronous exclusion without calendar date substitution.',
            '< 700 paired trading days returns insufficient data. Metric value is NULL.',
            'Zero tracking error halts with division by zero.',
            'Synchronous daily dates without data conflicts.',
            'Information ratio can be artificially inflated by low-active-risk strategies capturing minor timing anomalies. Zero tracking error causes mathematical singularity. Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;
END $$;
