-- V005__create_analysis_methodology.sql
-- YUKIRA MVP
-- Methodology, analysis runs, metrics, scores and decisions


CREATE TABLE methodology_version (
    id BIGSERIAL PRIMARY KEY,
    version_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_methodology_version_code
        UNIQUE (version_code),

    CONSTRAINT chk_methodology_version_status
        CHECK (
            status IN (
                'DRAFT',
                'ACTIVE',
                'RETIRED'
            )
        ),

    CONSTRAINT chk_methodology_version_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE analysis_run (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL,
    analysis_type VARCHAR(100) NOT NULL,
    analysis_date DATE NOT NULL,
    input_data_version VARCHAR(100),
    methodology_version_id BIGINT NOT NULL,
    configuration_version VARCHAR(100),
    application_version VARCHAR(100),
    model_version VARCHAR(100),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_analysis_run_scheme_option
        FOREIGN KEY (scheme_option_id)
        REFERENCES scheme_option(id),

    CONSTRAINT fk_analysis_run_methodology
        FOREIGN KEY (methodology_version_id)
        REFERENCES methodology_version(id),

    CONSTRAINT chk_analysis_run_status
        CHECK (
            status IN (
                'RUNNING',
                'COMPLETED',
                'FAILED',
                'INSUFFICIENT_EVIDENCE'
            )
        ),

    CONSTRAINT chk_analysis_run_completed
        CHECK (
            completed_at IS NULL
            OR completed_at >= started_at
        )
);


CREATE TABLE metric_definition (
    id BIGSERIAL PRIMARY KEY,
    metric_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    analytical_dimension VARCHAR(100) NOT NULL,
    metric_category VARCHAR(100),
    formula_definition TEXT,
    required_inputs TEXT,
    unit VARCHAR(50),
    directionality VARCHAR(30),
    minimum_history_days INTEGER,
    methodology_version_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_metric_definition_code
        UNIQUE (metric_code),

    CONSTRAINT fk_metric_definition_methodology
        FOREIGN KEY (methodology_version_id)
        REFERENCES methodology_version(id),

    CONSTRAINT chk_metric_directionality
        CHECK (
            directionality IS NULL
            OR directionality IN (
                'HIGHER_BETTER',
                'LOWER_BETTER',
                'NEUTRAL'
            )
        ),

    CONSTRAINT chk_metric_history
        CHECK (
            minimum_history_days IS NULL
            OR minimum_history_days >= 0
        )
);


CREATE TABLE metric_observation (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL,
    metric_definition_id BIGINT NOT NULL,
    observation_start_date DATE,
    observation_end_date DATE,
    result_value NUMERIC(30,12),
    unit VARCHAR(50),
    input_data_reference TEXT,
    calculation_status VARCHAR(30) NOT NULL DEFAULT 'CALCULATED',
    calculation_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    methodology_version_id BIGINT NOT NULL,
    quality_status VARCHAR(30) NOT NULL DEFAULT 'VALID',

    CONSTRAINT fk_metric_observation_analysis
        FOREIGN KEY (analysis_run_id)
        REFERENCES analysis_run(id),

    CONSTRAINT fk_metric_observation_metric
        FOREIGN KEY (metric_definition_id)
        REFERENCES metric_definition(id),

    CONSTRAINT fk_metric_observation_methodology
        FOREIGN KEY (methodology_version_id)
        REFERENCES methodology_version(id),

    CONSTRAINT chk_metric_observation_dates
        CHECK (
            observation_end_date IS NULL
            OR observation_start_date IS NULL
            OR observation_end_date >= observation_start_date
        ),

    CONSTRAINT chk_metric_calculation_status
        CHECK (
            calculation_status IN (
                'CALCULATED',
                'NOT_CALCULABLE',
                'INSUFFICIENT_DATA',
                'FAILED'
            )
        ),

    CONSTRAINT chk_metric_quality_status
        CHECK (
            quality_status IN (
                'VALID',
                'WARNING',
                'INVALID',
                'STALE'
            )
        )
);


CREATE TABLE score (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL,
    score_type VARCHAR(50) NOT NULL,
    score_value NUMERIC(12,6),
    methodology_version_id BIGINT NOT NULL,
    input_metric_reference TEXT,
    calculation_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'VALID',

    CONSTRAINT fk_score_analysis
        FOREIGN KEY (analysis_run_id)
        REFERENCES analysis_run(id),

    CONSTRAINT fk_score_methodology
        FOREIGN KEY (methodology_version_id)
        REFERENCES methodology_version(id),

    CONSTRAINT chk_score_type
        CHECK (
            score_type IN (
                'QUALITY',
                'OPPORTUNITY',
                'INVESTOR_FIT',
                'CONFIDENCE'
            )
        ),

    CONSTRAINT chk_score_value
        CHECK (
            score_value IS NULL
            OR (
                score_value >= 0
                AND score_value <= 100
            )
        ),

    CONSTRAINT chk_score_status
        CHECK (
            status IN (
                'VALID',
                'INVALID',
                'INSUFFICIENT_EVIDENCE'
            )
        )
);


CREATE TABLE investment_decision (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL,
    decision_outcome VARCHAR(30) NOT NULL,
    decision_rule_version VARCHAR(100) NOT NULL,
    quality_score_id BIGINT,
    opportunity_score_id BIGINT,
    investor_fit_score_id BIGINT,
    confidence_score_id BIGINT,
    decision_timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT fk_decision_analysis
        FOREIGN KEY (analysis_run_id)
        REFERENCES analysis_run(id),

    CONSTRAINT fk_decision_quality_score
        FOREIGN KEY (quality_score_id)
        REFERENCES score(id),

    CONSTRAINT fk_decision_opportunity_score
        FOREIGN KEY (opportunity_score_id)
        REFERENCES score(id),

    CONSTRAINT fk_decision_investor_fit_score
        FOREIGN KEY (investor_fit_score_id)
        REFERENCES score(id),

    CONSTRAINT fk_decision_confidence_score
        FOREIGN KEY (confidence_score_id)
        REFERENCES score(id),

    CONSTRAINT chk_decision_outcome
        CHECK (
            decision_outcome IN (
                'BUY',
                'HOLD',
                'WATCH',
                'INVESTIGATE',
                'REDUCE',
                'AVOID',
                'INSUFFICIENT_EVIDENCE'
            )
        )
);


CREATE INDEX idx_analysis_run_scheme_date
    ON analysis_run (scheme_option_id, analysis_date);

CREATE INDEX idx_analysis_run_methodology
    ON analysis_run (methodology_version_id);

CREATE INDEX idx_metric_definition_dimension
    ON metric_definition (analytical_dimension);

CREATE INDEX idx_metric_observation_analysis
    ON metric_observation (analysis_run_id);

CREATE INDEX idx_metric_observation_metric
    ON metric_observation (metric_definition_id);

CREATE INDEX idx_score_analysis
    ON score (analysis_run_id);

CREATE INDEX idx_score_type
    ON score (score_type);

CREATE INDEX idx_decision_analysis
    ON investment_decision (analysis_run_id);