-- Phase 2S: Seed for MKT-03 (Upside Capture 3Y), MKT-04 (Downside Capture 3Y), and MKT-05 (Capture Spread 3Y) Metric Definitions and Candidate Methodology Versions

-- 1. Seed Metric Definitions
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'MKT-03',
    'Upside Capture Ratio (3Y)',
    'MARKET_SENSITIVITY',
    'CAPTURE_RATIO',
    'Measures the return captured by the scheme during periods when the benchmark generated positive returns over 36 calendar months.',
    '((prod_{t in Up}(1 + R_p,t) - 1) / (prod_{t in Up}(1 + R_b,t) - 1)) * 100',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'MKT-04',
    'Downside Capture Ratio (3Y)',
    'MARKET_SENSITIVITY',
    'CAPTURE_RATIO',
    'Measures the return captured by the scheme during periods when the benchmark generated negative returns over 36 calendar months.',
    '((prod_{t in Down}(1 + R_p,t) - 1) / (prod_{t in Down}(1 + R_b,t) - 1)) * 100',
    'PERCENTAGE',
    'DAILY',
    TRUE,
    FALSE,
    TRUE
),
(
    'MKT-05',
    'Capture Spread (3Y)',
    'MARKET_SENSITIVITY',
    'CAPTURE_RATIO',
    'Evaluates capture asymmetry by calculating the net difference between Upside Capture and Downside Capture (UC_3Y - DC_3Y).',
    'UC_3Y - DC_3Y',
    'PERCENTAGE_POINTS',
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
    v_metric_id_03 BIGINT;
    v_metric_id_04 BIGINT;
    v_metric_id_05 BIGINT;
BEGIN
    SELECT id INTO v_metric_id_03 FROM metric_definition WHERE metric_code = 'MKT-03';
    SELECT id INTO v_metric_id_04 FROM metric_definition WHERE metric_code = 'MKT-04';
    SELECT id INTO v_metric_id_05 FROM metric_definition WHERE metric_code = 'MKT-05';

    -- MKT-03 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'MKT_03_3Y_UPSIDE_CAPTURE' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_03, 'MKT_03_3Y_UPSIDE_CAPTURE', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_upside_observations": 150}'::jsonb,
            'UC = ((prod_{t in Up}(1 + R_p,t) - 1) / (prod_{t in Up}(1 + R_b,t) - 1)) * 100 where Up = {t: R_b,t > 0}',
            'Upside Capture Ratio over 36 calendar months',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months candidate analytical window',
            'Subsampled paired trading days where R_b,t > 0.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE', 'CUMULATIVE_BENCHMARK_UPSIDE_GT_ZERO',
            'Paired synchronous exclusion without calendar date substitution.',
            '< 150 positive benchmark days returns insufficient data. Metric value is NULL.',
            'Zero cumulative benchmark upside return halts execution.',
            'Synchronous daily dates without data conflicts.',
            'Non-contiguous compounding creates path-dependent cumulative return over an artificial timeline. Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;

    -- MKT-04 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'MKT_04_3Y_DOWNSIDE_CAPTURE' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_04, 'MKT_04_3Y_DOWNSIDE_CAPTURE', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_downside_observations": 100}'::jsonb,
            'DC = ((prod_{t in Down}(1 + R_p,t) - 1) / (prod_{t in Down}(1 + R_b,t) - 1)) * 100 where Down = {t: R_b,t < 0}',
            'Downside Capture Ratio over 36 calendar months',
            'Daily NAV series and Benchmark TRI series over 36 months',
            'Daily paired observations', '36 calendar months candidate analytical window',
            'Subsampled paired trading days where R_b,t < 0.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'UNANNUALIZED_SUBSET_PRODUCT_CANDIDATE', 'CUMULATIVE_BENCHMARK_DOWNSIDE_LT_ZERO',
            'Paired synchronous exclusion without calendar date substitution.',
            '< 100 negative benchmark days returns insufficient data. Metric value is NULL.',
            'Zero cumulative benchmark downside return halts execution.',
            'Synchronous daily dates without data conflicts.',
            'Non-contiguous compounding creates path-dependent cumulative return over an artificial timeline. Inverse capture gains (fund gains during benchmark declines) produce negative ratios requiring diagnostic clarification. Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;

    -- MKT-05 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'MKT_05_3Y_CAPTURE_SPREAD' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_05, 'MKT_05_3Y_CAPTURE_SPREAD', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{}'::jsonb,
            'Spread_capture = UC_3Y - DC_3Y',
            'Capture Spread over 36 calendar months',
            'MKT-03 (Upside Capture) and MKT-04 (Downside Capture)',
            'Derived from constituent capture metrics', '36 calendar months',
            'Derived synchronous calendar alignment.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE_DIFFERENCE_BETWEEN_PERCENTAGES', 'NONE',
            'If either constituent capture metric is missing or insufficient, spread is NULL.',
            'Inherited from constituent capture metrics.',
            'Inherited from constituent capture metrics.',
            'Verified constituent capture metrics.',
            'Linear spread ignores constituent scaling factors (e.g. 120 - 100 = 20 vs 80 - 60 = 20). Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;
END $$;
