-- Phase 2R: Seed for RSK-06 (Historical VaR 95%) and RSK-07 (Expected Shortfall / CVaR 95%) Metric Definitions and Candidate Methodology Versions

-- 1. Seed Metric Definitions
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'RSK-06',
    'Historical Value at Risk (95% 3Y)',
    'RISK',
    'TAIL_RISK',
    'Identifies the minimum daily loss magnitude expected on the worst 5% of trading days based on the empirical return distribution over 36 calendar months.',
    '-Q_0.05(R_1, ..., R_N)',
    'PERCENTAGE',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
),
(
    'RSK-07',
    'Historical Expected Shortfall (95% 3Y)',
    'RISK',
    'TAIL_RISK',
    'Measures the average loss experienced on days when returns breach the 95% historical VaR threshold over 36 calendar months (Conditional VaR).',
    '- (1 / |T_tail|) * sum(R_t for R_t <= Q_0.05)',
    'PERCENTAGE',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
)
ON CONFLICT (metric_code) DO UPDATE SET
    metric_name = EXCLUDED.metric_name,
    purpose = EXCLUDED.purpose,
    formula_display = EXCLUDED.formula_display,
    units = EXCLUDED.units;

-- 2. Seed/Update Initial Candidate Methodology Versions
DO $$
DECLARE
    v_metric_id_06 BIGINT;
    v_metric_id_07 BIGINT;
BEGIN
    SELECT id INTO v_metric_id_06 FROM metric_definition WHERE metric_code = 'RSK-06';
    SELECT id INTO v_metric_id_07 FROM metric_definition WHERE metric_code = 'RSK-07';

    -- RSK-06 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_06_3Y_HISTORICAL_VAR_95' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_06, 'RSK_06_3Y_HISTORICAL_VAR_95', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"confidence_level": 0.95, "min_observations": 700}'::jsonb,
            'VaR_0.95 = -Q_0.05(R_1, ..., R_N) using linear empirical quantile interpolation',
            'Historical empirical 95% 1-day Value at Risk over 3-year return distribution',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Daily simple returns: R_t = (NAV_t / NAV_{t-1}) - 1.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE_1DAY_HORIZON', 'QUANTILE_RANK_POSITION',
            'Multi-day returns across non-trading weekends/holidays are included as single discrete observations.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-finite returns or NAV <= 0 halts execution.',
            'Clean daily series without unresolved data conflicts.',
            'Historical VaR is not a coherent risk measure (non-subadditive) and ignores the magnitude of tail losses beyond the 95th percentile threshold.',
            FALSE
        );
    END IF;

    -- RSK-07 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_07_3Y_EXPECTED_SHORTFALL_95' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_07, 'RSK_07_3Y_EXPECTED_SHORTFALL_95', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"confidence_level": 0.95, "min_observations": 700}'::jsonb,
            'ES_0.95 = - (1 / |T_tail|) * sum(R_t for R_t <= Q_0.05)',
            'Historical empirical Expected Shortfall (CVaR) at 95% confidence over 3-year return distribution',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Daily simple returns: R_t = (NAV_t / NAV_{t-1}) - 1.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE_1DAY_HORIZON', 'TAIL_OBSERVATION_COUNT',
            'Multi-day returns across non-trading weekends/holidays are included as single discrete observations.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-finite returns or NAV <= 0 halts execution.',
            'Clean daily series without unresolved data conflicts.',
            'Small tail sample size (~35 observations) increases historical sampling variance. Candidate methodology, not validated for production.',
            FALSE
        );
    END IF;
END $$;
