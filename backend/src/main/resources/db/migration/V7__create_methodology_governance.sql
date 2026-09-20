-- Phase 2G: Stage 1 - Methodology Governance and Metric Definition Infrastructure

-- 1. Metric Definition Master Table
CREATE TABLE metric_definition (
    id BIGSERIAL PRIMARY KEY,
    metric_code VARCHAR(50) NOT NULL UNIQUE,
    metric_name VARCHAR(255) NOT NULL,
    analytical_dimension VARCHAR(50) NOT NULL,
    metric_category VARCHAR(50) NOT NULL,
    purpose TEXT NOT NULL,
    formula_display TEXT NOT NULL,
    units VARCHAR(30) NOT NULL,
    default_frequency VARCHAR(30) NOT NULL,
    benchmark_required BOOLEAN NOT NULL DEFAULT FALSE,
    risk_free_required BOOLEAN NOT NULL DEFAULT FALSE,
    point_in_time_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Enhance methodology_version with auditable governance attributes
ALTER TABLE methodology_version
    ADD COLUMN metric_definition_id BIGINT REFERENCES metric_definition(id),
    ADD COLUMN mathematical_definition TEXT,
    ADD COLUMN formula_reference VARCHAR(255),
    ADD COLUMN required_inputs TEXT,
    ADD COLUMN frequency_assumptions VARCHAR(100),
    ADD COLUMN lookback_rule TEXT,
    ADD COLUMN observation_date_semantics VARCHAR(100),
    ADD COLUMN information_set_requirement TEXT,
    ADD COLUMN annualization_convention VARCHAR(50),
    ADD COLUMN denominator_convention VARCHAR(50),
    ADD COLUMN missing_data_rule TEXT,
    ADD COLUMN insufficient_history_rule TEXT,
    ADD COLUMN invalid_data_rule TEXT,
    ADD COLUMN quality_prerequisites TEXT,
    ADD COLUMN known_limitations TEXT,
    ADD COLUMN lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    ADD COLUMN validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    ADD COLUMN validation_evidence_reference TEXT,
    ADD COLUMN approval_record TEXT,
    ADD COLUMN approved_by VARCHAR(100),
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN supersedes_version_id BIGINT REFERENCES methodology_version(id),
    ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_methodology_lifecycle_status
        CHECK (lifecycle_status IN ('CANDIDATE', 'VALIDATED', 'APPROVED', 'RETIRED')),
    ADD CONSTRAINT chk_methodology_validation_status
        CHECK (validation_status IN ('UNVALIDATED', 'VALIDATED')),
    ADD CONSTRAINT chk_methodology_approved_requires_validation
        CHECK (lifecycle_status <> 'APPROVED' OR (validation_status = 'VALIDATED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND approval_record IS NOT NULL)),
    ADD CONSTRAINT chk_methodology_validated_requires_evidence
        CHECK (validation_status <> 'VALIDATED' OR validation_evidence_reference IS NOT NULL);

-- 3. Methodology Change Log Table (Auditable Event History)
CREATE TABLE methodology_change_log (
    id BIGSERIAL PRIMARY KEY,
    methodology_version_id BIGINT NOT NULL REFERENCES methodology_version(id),
    transition_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_methodology_change_ver ON methodology_change_log (methodology_version_id);
CREATE INDEX idx_metric_def_dimension ON metric_definition (analytical_dimension);

-- 4. Initial Seed for RET-02 Metric Definition
INSERT INTO metric_definition (
    metric_code, metric_name, analytical_dimension, metric_category,
    purpose, formula_display, units, default_frequency,
    benchmark_required, risk_free_required, point_in_time_required
) VALUES (
    'RET-02',
    'Simple Period Return',
    'RETURNS',
    'PERFORMANCE',
    'Computes discrete percentage price change between two temporal boundaries for a scheme option.',
    '(NAV_end - NAV_start) / NAV_start',
    'PERCENTAGE',
    'DISCRETE_PERIOD',
    FALSE,
    FALSE,
    TRUE
) ON CONFLICT (metric_code) DO NOTHING;

-- 5. Seed/Update RET_02_SIMPLE_RETURN / CANDIDATE_V1 governance record
DO $$
DECLARE
    v_metric_id BIGINT;
    v_existing_ver_id BIGINT;
BEGIN
    SELECT id INTO v_metric_id FROM metric_definition WHERE metric_code = 'RET-02';

    SELECT id INTO v_existing_ver_id
    FROM methodology_version
    WHERE methodology_code = 'RET_02_SIMPLE_RETURN' AND version_tag = 'CANDIDATE_V1';

    IF v_existing_ver_id IS NOT NULL THEN
        UPDATE methodology_version SET
            metric_definition_id = v_metric_id,
            mathematical_definition = '(NAV_end - NAV_start) / NAV_start',
            formula_reference = 'Standard discrete financial return (Bacon, 2008)',
            required_inputs = 'Historical NAV observations for scheme_option at requested start and end dates',
            frequency_assumptions = 'Discrete period boundary resolution from daily AMFI NAV history',
            lookback_rule = 'Candidate lookback window up to 4 calendar days preceding requested boundary date',
            observation_date_semantics = 'EFFECTIVE_DATE_AS_OF_BOUNDARY',
            information_set_requirement = 'Strictly availability_time <= knowledgeCutoffTime; latest eligible availability with deterministic tie-break',
            annualization_convention = 'NONE',
            denominator_convention = 'STARTING_NAV',
            missing_data_rule = 'Look back up to 4 calendar days preceding target date. If no eligible observation found within window, halt with INSUFFICIENT_EVIDENCE.',
            insufficient_history_rule = 'Both start and end boundary observations must resolve. If either boundary missing, halt calculation with INSUFFICIENT_EVIDENCE.',
            invalid_data_rule = 'NAV <= 0 rejected as INVALID. Unresolvable authority conflicts halt with SUSPICIOUS/CONFLICTING diagnostics.',
            quality_prerequisites = 'Boundary observations must be VALID or pass lookback triage without unresolvable ambiguity.',
            known_limitations = 'Discrete return does not account for intraday or intra-period cash flows, dividends, or compounding. 4-day lookback is a candidate convention not yet empirically validated.',
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
            'RET_02_SIMPLE_RETURN',
            'CANDIDATE_V1',
            'CANDIDATE',
            'CANDIDATE',
            'UNVALIDATED',
            '3eadee476c37eafea221f42167d48380ae0ef5b9',
            '{}'::jsonb,
            '(NAV_end - NAV_start) / NAV_start',
            'Standard discrete financial return (Bacon, 2008)',
            'Historical NAV observations for scheme_option at requested start and end dates',
            'Discrete period boundary resolution from daily AMFI NAV history',
            'Candidate lookback window up to 4 calendar days preceding requested boundary date',
            'EFFECTIVE_DATE_AS_OF_BOUNDARY',
            'Strictly availability_time <= knowledgeCutoffTime; latest eligible availability with deterministic tie-break',
            'NONE',
            'STARTING_NAV',
            'Look back up to 4 calendar days preceding target date. If no eligible observation found within window, halt with INSUFFICIENT_EVIDENCE.',
            'Both start and end boundary observations must resolve. If either boundary missing, halt calculation with INSUFFICIENT_EVIDENCE.',
            'NAV <= 0 rejected as INVALID. Unresolvable authority conflicts halt with SUSPICIOUS/CONFLICTING diagnostics.',
            'Boundary observations must be VALID or pass lookback triage without unresolvable ambiguity.',
            'Discrete return does not account for intraday or intra-period cash flows, dividends, or compounding. 4-day lookback is a candidate convention not yet empirically validated.',
            FALSE
        );
    END IF;
END $$;
