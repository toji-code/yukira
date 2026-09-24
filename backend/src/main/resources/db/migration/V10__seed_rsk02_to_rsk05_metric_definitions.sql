-- Phase 2M: Seed for RSK-02, RSK-03, RSK-04, RSK-05 Metric Definitions and Initial Candidate Methodology Versions

-- 1. Seed Metric Definitions
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES
(
    'RSK-02',
    'Downside Semideviation (3Y)',
    'RISK',
    'DOWNSIDE_RISK',
    'Isolates downside risk by measuring the dispersion of returns below a target return threshold (MAR=0) over 36 calendar months.',
    'sqrt( (1 / (N - 1)) * sum(min(R_t - MAR, 0)^2) ) * sqrt(252)',
    'PERCENTAGE',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
),
(
    'RSK-03',
    '3-Year Maximum Drawdown',
    'RISK',
    'DRAWDOWN',
    'Measures the worst peak-to-trough percentage capital loss over a 36-month horizon.',
    'min( (Value_t / Running_Peak_t) - 1 )',
    'PERCENTAGE',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
),
(
    'RSK-04',
    'Maximum Drawdown Duration',
    'RISK',
    'DRAWDOWN_DURATION',
    'Measures the maximum length of time (in calendar days) the series remains below its previous high-water mark.',
    'max( ElapsedPeriod(Peak -> Recovery) )',
    'DAYS',
    'DAILY',
    FALSE,
    FALSE,
    TRUE
),
(
    'RSK-05',
    'Ulcer Index (3Y)',
    'RISK',
    'UNDERWATER_STRESS',
    'Measures the depth and duration of drawdowns quadratically over 36 calendar months, penalizing prolonged underwater periods.',
    'sqrt( (1 / N) * sum((100 * (Value_t / Running_Peak_t - 1))^2) )',
    'POINTS',
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
    v_metric_id_02 BIGINT;
    v_metric_id_03 BIGINT;
    v_metric_id_04 BIGINT;
    v_metric_id_05 BIGINT;
BEGIN
    SELECT id INTO v_metric_id_02 FROM metric_definition WHERE metric_code = 'RSK-02';
    SELECT id INTO v_metric_id_03 FROM metric_definition WHERE metric_code = 'RSK-03';
    SELECT id INTO v_metric_id_04 FROM metric_definition WHERE metric_code = 'RSK-04';
    SELECT id INTO v_metric_id_05 FROM metric_definition WHERE metric_code = 'RSK-05';

    -- RSK-02 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_02_3Y_DOWNSIDE_DEVIATION' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_02, 'RSK_02_3Y_DOWNSIDE_DEVIATION', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"target_return": 0.0, "periods_per_year": 252, "min_observations": 700, "denominator": "N-1"}'::jsonb,
            'sigma_down = sqrt( (1 / (N - 1)) * sum(min(R_t - MAR, 0)^2) ) * sqrt(252)',
            'Downside semideviation with N-1 divisor, MAR=0 hurdle, and sqrt(252) annualization',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Daily simple returns: R_t = (NAV_t / NAV_{t-1}) - 1.',
            'Strictly availability_time <= knowledgeCutoffTime',
            'SQRT_252_CANDIDATE', 'N_MINUS_ONE_CANDIDATE',
            'Multi-day returns across non-trading weekends/holidays are included as single discrete observations.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-finite returns or NAV <= 0 halts execution.',
            'Clean daily series without unresolved data conflicts.',
            'Downside semideviation denominator convention (N versus N-1) and MAR hurdle (0 vs Rf) are candidate conventions requiring validation.',
            FALSE
        );
    END IF;

    -- RSK-03 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_03_3Y_MAX_DRAWDOWN' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_03, 'RSK_03_3Y_MAX_DRAWDOWN', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_observations": 700}'::jsonb,
            'Running_Peak_t = max(NAV_1 ... NAV_t); Drawdown_t = (NAV_t / Running_Peak_t) - 1; Max_Drawdown = min(Drawdown_t)',
            'Peak-to-trough maximum percentage capital decline over 3-year valuation path',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Sequential daily valuation series',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE_DISCRETE_PATH', 'RUNNING_PEAK_NAV_CANDIDATE',
            'Gaps between trading days do not reset running peak.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-positive NAV halts execution.',
            'Continuous chronologically ordered NAVs without unresolved data conflicts.',
            'Single-event metric; ignores frequency, depth, and duration of secondary drawdowns.',
            FALSE
        );
    END IF;

    -- RSK-04 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_04_MAX_DRAWDOWN_DURATION' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_04, 'RSK_04_MAX_DRAWDOWN_DURATION', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_observations": 700, "unit": "CALENDAR_DAYS"}'::jsonb,
            'Duration = max(ElapsedCalendarDays(Peak -> Recovery)); Ongoing drawdown measured from peak to cutoff',
            'Maximum elapsed period from prior high-water mark to recovery (or to knowledge cutoff if ongoing)',
            'Daily NAV series with calendar dates over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Observation dates of identified peak, trough, and recovery points',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE', 'NONE',
            'Calendar day elapsed difference between observation dates.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-positive values halt execution.',
            'Clean daily series without unverified data revisions.',
            'For unresolved drawdowns at the cutoff date, true total recovery duration is right-censored.',
            FALSE
        );
    END IF;

    -- RSK-05 Methodology Version
    IF NOT EXISTS (SELECT 1 FROM methodology_version WHERE methodology_code = 'RSK_05_3Y_ULCER_INDEX' AND version_tag = 'CANDIDATE_V1') THEN
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
            v_metric_id_05, 'RSK_05_3Y_ULCER_INDEX', 'CANDIDATE_V1',
            'CANDIDATE', 'CANDIDATE', 'UNVALIDATED',
            'pending_commit', '{"min_observations": 700, "denominator": "N"}'::jsonb,
            'Pct_DD_t = 100 * ((NAV_t / Running_Peak_t) - 1); Ulcer_Index = sqrt((1 / N) * sum(Pct_DD_t^2))',
            'Root mean square of percentage drawdowns from historical running peak over 3-year horizon',
            'Daily NAV series over 36 calendar months for scheme option',
            'Daily NAV', '36 calendar months candidate analytical window',
            'Chronological daily NAV series',
            'Strictly availability_time <= knowledgeCutoffTime',
            'NONE', 'N_OBSERVATIONS_CANDIDATE',
            'Peak carried across non-trading days.',
            '< 700 valid trading days returns insufficient data. Metric value is NULL.',
            'Non-positive values halt execution.',
            'Unbroken chronological time series.',
            'Abstract score of underwater friction rather than an intuitive percentage return loss.',
            FALSE
        );
    END IF;
END $$;
