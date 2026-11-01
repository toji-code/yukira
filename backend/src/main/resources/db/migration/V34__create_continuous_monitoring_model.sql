-- Flyway Migration V34: Continuous Fund & Portfolio Monitoring Model
-- Schema authority for continuous evidence drift, thesis reassessment, and investor alerts.

CREATE TABLE investor_monitoring_condition (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_id BIGINT REFERENCES scheme(id),
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    target_scope VARCHAR(32) NOT NULL DEFAULT 'SCHEME_OPTION',
    condition_name VARCHAR(255) NOT NULL,
    evidence_field VARCHAR(64) NOT NULL,
    operator VARCHAR(32) NOT NULL,
    threshold_value VARCHAR(64),
    threshold_numeric NUMERIC(19, 4),
    associated_thesis_key VARCHAR(128),
    thesis_reconsideration_guidance TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    evaluation_state VARCHAR(32) NOT NULL DEFAULT 'NOT_EVALUATED',
    last_evaluated_value VARCHAR(255),
    last_evaluated_at TIMESTAMP WITH TIME ZONE,
    last_observation_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_cond_investor ON investor_monitoring_condition(investor_id);
CREATE INDEX idx_monitoring_cond_option ON investor_monitoring_condition(scheme_option_id);
CREATE INDEX idx_monitoring_cond_thesis ON investor_monitoring_condition(investor_id, associated_thesis_key);

CREATE TABLE investor_monitoring_event (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_id BIGINT REFERENCES scheme(id),
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    portfolio_scope VARCHAR(32),
    event_classification VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    metric_or_evidence_id VARCHAR(64) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    previous_value VARCHAR(255),
    current_value VARCHAR(255),
    change_magnitude NUMERIC(19, 4),
    unit_or_format VARCHAR(32),
    previous_observation_date DATE,
    current_observation_date DATE,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE,
    source_reference VARCHAR(255),
    calculation_run_id BIGINT REFERENCES calculation_run(id),
    metric_registry_version VARCHAR(32) DEFAULT 'V1',
    methodology_version VARCHAR(32) DEFAULT 'PHASE_2H_FROZEN',
    evidence_coverage_info TEXT,
    explanation TEXT NOT NULL,
    trigger_rationale TEXT,
    known_limitations TEXT,
    is_data_artifact BOOLEAN NOT NULL DEFAULT FALSE,
    artifact_explanation TEXT,
    dedup_hash VARCHAR(64),
    associated_thesis_key VARCHAR(128),
    associated_condition_id BIGINT REFERENCES investor_monitoring_condition(id) ON DELETE SET NULL,
    associated_follow_up_id VARCHAR(128),
    investigation_notes TEXT,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    investigating_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    dismissed_at TIMESTAMP WITH TIME ZONE,
    resolution_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_event_investor ON investor_monitoring_event(investor_id, lifecycle_status);
CREATE INDEX idx_monitoring_event_option ON investor_monitoring_event(scheme_option_id);
CREATE INDEX idx_monitoring_event_dedup ON investor_monitoring_event(investor_id, dedup_hash);
CREATE INDEX idx_monitoring_event_created ON investor_monitoring_event(investor_id, created_at DESC);

CREATE TABLE investor_monitoring_event_note (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES investor_monitoring_event(id) ON DELETE CASCADE,
    note_text TEXT NOT NULL,
    author VARCHAR(128) NOT NULL DEFAULT 'INVESTOR',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_note_event ON investor_monitoring_event_note(event_id, created_at DESC);

