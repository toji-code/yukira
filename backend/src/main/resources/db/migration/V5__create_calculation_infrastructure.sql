-- Phase 2D: Stage 5 - Calculation Infrastructure, Metrics & Quality Triage

CREATE TABLE methodology_version (
    id BIGSERIAL PRIMARY KEY,
    methodology_code VARCHAR(100) NOT NULL,
    version_tag VARCHAR(50) NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    git_commit_hash VARCHAR(40) NOT NULL,
    parameter_configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_methodology_ver UNIQUE (methodology_code, version_tag)
);

CREATE TABLE calculation_run (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    as_of_date DATE NOT NULL,
    knowledge_cutoff_time TIMESTAMPTZ NOT NULL,
    methodology_version_id BIGINT NOT NULL REFERENCES methodology_version(id),
    engine_software_version VARCHAR(100) NOT NULL,
    input_snapshot_sha256 VARCHAR(64),
    execution_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_completed_at TIMESTAMPTZ,
    run_status VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT
);

CREATE TABLE metric_result (
    id BIGSERIAL PRIMARY KEY,
    calculation_run_id BIGINT NOT NULL REFERENCES calculation_run(id),
    metric_code VARCHAR(50) NOT NULL,
    period_type VARCHAR(30) NOT NULL DEFAULT '1Y',
    numeric_value NUMERIC(30, 10),
    string_value VARCHAR(100),
    units VARCHAR(30) NOT NULL,
    calculation_status VARCHAR(30) NOT NULL DEFAULT 'CALCULATED',
    diagnostics JSONB,
    error_message TEXT,
    CONSTRAINT uq_run_metric_period UNIQUE (calculation_run_id, metric_code, period_type)
);

CREATE TABLE calculation_run_input_observation (
    id BIGSERIAL PRIMARY KEY,
    calculation_run_id BIGINT NOT NULL REFERENCES calculation_run(id) ON DELETE CASCADE,
    nav_observation_id BIGINT REFERENCES nav_observation(id),
    benchmark_observation_id BIGINT REFERENCES benchmark_observation(id),
    effective_date DATE NOT NULL,
    revision_seq INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_run_input_exactly_one_fk CHECK (
        (nav_observation_id IS NOT NULL AND benchmark_observation_id IS NULL) OR
        (nav_observation_id IS NULL AND benchmark_observation_id IS NOT NULL)
    ),
    CONSTRAINT uq_run_input_nav UNIQUE (calculation_run_id, nav_observation_id),
    CONSTRAINT uq_run_input_benchmark UNIQUE (calculation_run_id, benchmark_observation_id)
);

CREATE TABLE validation_issue (
    id BIGSERIAL PRIMARY KEY,
    target_entity_type VARCHAR(50) NOT NULL,
    target_entity_id BIGINT NOT NULL,
    check_code VARCHAR(50) NOT NULL,
    quality_assessment VARCHAR(30) NOT NULL DEFAULT 'VALID',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    revision_status VARCHAR(30) NOT NULL DEFAULT 'ORIGINAL',
    temporal_status VARCHAR(30) NOT NULL DEFAULT 'CURRENT',
    presence_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    integrity_condition VARCHAR(30) NOT NULL DEFAULT 'NONE',
    message TEXT,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_calc_run_lookup ON calculation_run (
    scheme_option_id, as_of_date, methodology_version_id
);
CREATE INDEX idx_metric_result_run ON metric_result (
    calculation_run_id, metric_code
);
CREATE INDEX idx_run_input_obs_run ON calculation_run_input_observation (
    calculation_run_id
);
CREATE INDEX idx_run_input_obs_nav ON calculation_run_input_observation (
    nav_observation_id
);
CREATE INDEX idx_run_input_obs_bm ON calculation_run_input_observation (
    benchmark_observation_id
);
CREATE INDEX idx_validation_issue_target ON validation_issue (
    target_entity_type, target_entity_id
);
