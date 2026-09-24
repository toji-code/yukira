-- Phase 2L: Seed for RSK-01 (3-Year Annualized Volatility) Metric Definition and Methodology Version

-- 1. Initial Seed for RSK-01 Metric Definition
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES (
    'RSK-01',
    '3-Year Annualized Volatility',
    'RISK',
    'VOLATILITY',
    'Measures total return dispersion of daily returns around their sample mean over 36 calendar months.',
    'sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ) * sqrt(252)',
    'PERCENTAGE',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
) ON CONFLICT (metric_code) DO NOTHING;

-- 2. Seed/Update RSK_01_3Y_VOLATILITY / CANDIDATE_V1 governance record
DO $$
DECLARE
    v_metric_id BIGINT;
    v_existing_ver_id BIGINT;
BEGIN
    SELECT id INTO v_metric_id FROM metric_definition WHERE metric_code = 'RSK-01';

    SELECT id INTO v_existing_ver_id
    FROM methodology_version
    WHERE methodology_code = 'RSK_01_3Y_VOLATILITY' AND version_tag = 'CANDIDATE_V1';

    IF v_existing_ver_id IS NOT NULL THEN
        UPDATE methodology_version SET
            metric_definition_id = v_metric_id,
            mathematical_definition = 's = sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ); sigma_annual = s * sqrt(252)',
            formula_reference = 'Sample standard deviation with N-1 divisor and sqrt(252) annualization',
            required_inputs = 'Daily NAV series over 36 calendar months for scheme option',
            frequency_assumptions = 'Daily NAV',
            lookback_rule = '36 calendar months candidate analytical window',
            observation_date_semantics = 'Daily simple returns: R_t = (NAV_t / NAV_{t-1}) - 1.',
            information_set_requirement = 'Strictly availability_time <= knowledgeCutoffTime',
            annualization_convention = 'SQRT_252_CANDIDATE',
            denominator_convention = 'N_MINUS_ONE_CANDIDATE',
            missing_data_rule = 'Multi-day returns across non-trading weekends/holidays are included as single discrete observations.',
            insufficient_history_rule = '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            invalid_data_rule = 'Non-finite returns or NAV <= 0 halts execution.',
            quality_prerequisites = 'Clean daily series without unresolved data conflicts.',
            known_limitations = 'Treats upside variance identically to downside variance; assumes identical independent distribution. sqrt(252) annualization and N-1 divisor are candidate conventions requiring validation; no annualization convention is approved for production.',
            lifecycle_status = 'CANDIDATE',
            approval_status = 'CANDIDATE',
            validation_status = 'UNVALIDATED',
            validation_evidence_reference = NULL,
            approval_record = NULL,
            approved_by = NULL,
            approved_at = NULL,
            is_locked = EXISTS (SELECT 1 FROM calculation_run cr WHERE cr.methodology_version_id = v_existing_ver_id)
        WHERE id = v_existing_ver_id;
    ELSE
        INSERT INTO methodology_version (
            metric_definition_id,
            methodology_code,
            version_tag,
            approval_status,
            lifecycle_status,
            validation_status,
            git_commit_hash,
            parameter_configuration,
            mathematical_definition,
            formula_reference,
            required_inputs,
            frequency_assumptions,
            lookback_rule,
            observation_date_semantics,
            information_set_requirement,
            annualization_convention,
            denominator_convention,
            missing_data_rule,
            insufficient_history_rule,
            invalid_data_rule,
            quality_prerequisites,
            known_limitations,
            is_locked
        ) VALUES (
            v_metric_id,
            'RSK_01_3Y_VOLATILITY',
            'CANDIDATE_V1',
            'CANDIDATE',
            'CANDIDATE',
            'UNVALIDATED',
            'pending_commit',
            '{"periods_per_year": 252, "min_observations": 700, "denominator": "N-1"}'::jsonb,
            's = sqrt( (1 / (N - 1)) * sum((R_t - R_mean)^2) ); sigma_annual = s * sqrt(252)',
            'Sample standard deviation with N-1 divisor and sqrt(252) annualization',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV',
            '36 calendar months candidate analytical window',
            'Daily simple returns: R_t = (NAV_t / NAV_{t-1}) - 1.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'SQRT_252_CANDIDATE',
            'N_MINUS_ONE_CANDIDATE',
            'Multi-day returns across non-trading weekends/holidays are included as single discrete observations.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-finite returns or NAV <= 0 halts execution.',
            'Clean daily series without unresolved data conflicts.',
            'Treats upside variance identically to downside variance; assumes identical independent distribution. sqrt(252) annualization and N-1 divisor are candidate conventions requiring validation; no annualization convention is approved for production.',
            FALSE
        );
    END IF;
END $$;
