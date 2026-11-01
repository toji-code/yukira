-- Flyway Migration V37: Create Stress Investigation Finding, Data-Quality Signal, and Calculation Manifest Repository
-- Schema authority for persistent stress findings, lifecycle supersession, lineage verification, and research continuity (Tasks 80-85, 89, 96).

CREATE TABLE investor_stress_finding (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    portfolio_id BIGINT,
    finding_id VARCHAR(100) NOT NULL,
    finding_version INT NOT NULL DEFAULT 1,
    calculation_run_id BIGINT REFERENCES calculation_run(id),
    calculation_run_code VARCHAR(100),
    snapshot_id VARCHAR(100),
    snapshot_fingerprint VARCHAR(64),
    episode_code VARCHAR(50) NOT NULL,
    episode_name VARCHAR(200),
    finding_type VARCHAR(100) NOT NULL,
    finding_severity VARCHAR(50) NOT NULL,
    finding_headline TEXT NOT NULL,
    finding_detail TEXT,
    methodology_version VARCHAR(50) NOT NULL DEFAULT 'PORTFOLIO_STRESS_METHODOLOGY_V2',
    governance_status VARCHAR(50) NOT NULL DEFAULT 'CANDIDATE',
    knowledge_cutoff_date TIMESTAMP WITH TIME ZONE,
    as_of_date DATE,
    lifecycle_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    superseded_by_finding_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investor_stress_finding_investor ON investor_stress_finding(investor_id);
CREATE INDEX idx_investor_stress_finding_portfolio ON investor_stress_finding(portfolio_id);
CREATE INDEX idx_investor_stress_finding_finding_id ON investor_stress_finding(finding_id);
CREATE INDEX idx_investor_stress_finding_lifecycle ON investor_stress_finding(investor_id, lifecycle_status);
CREATE INDEX idx_investor_stress_finding_run ON investor_stress_finding(calculation_run_id);
CREATE INDEX idx_investor_stress_finding_run_code ON investor_stress_finding(calculation_run_code);

CREATE TABLE investor_stress_data_quality_signal (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    portfolio_id BIGINT,
    calculation_run_id BIGINT REFERENCES calculation_run(id),
    calculation_run_code VARCHAR(100),
    signal_code VARCHAR(100) NOT NULL,
    signal_dimension VARCHAR(50) NOT NULL,
    signal_state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE_WARNING',
    constituent_code VARCHAR(50),
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    source_revision_seq INT,
    message TEXT NOT NULL,
    evidence_context TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stress_dq_signal_investor ON investor_stress_data_quality_signal(investor_id);
CREATE INDEX idx_stress_dq_signal_run ON investor_stress_data_quality_signal(calculation_run_id);
CREATE INDEX idx_stress_dq_signal_run_code ON investor_stress_data_quality_signal(calculation_run_code);
CREATE INDEX idx_stress_dq_signal_state ON investor_stress_data_quality_signal(investor_id, signal_state);

CREATE TABLE investor_stress_calculation_manifest (
    id BIGSERIAL PRIMARY KEY,
    calculation_run_id BIGINT REFERENCES calculation_run(id),
    calculation_run_code VARCHAR(100) NOT NULL,
    portfolio_id BIGINT,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    snapshot_id VARCHAR(100),
    snapshot_fingerprint VARCHAR(64),
    input_hash VARCHAR(64) NOT NULL,
    methodology_version VARCHAR(50) NOT NULL DEFAULT 'PORTFOLIO_STRESS_METHODOLOGY_V2',
    governance_status VARCHAR(50) NOT NULL DEFAULT 'CANDIDATE',
    knowledge_cutoff TIMESTAMP WITH TIME ZONE,
    analysis_cutoff_date DATE,
    manifest_payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stress_calc_manifest_run ON investor_stress_calculation_manifest(calculation_run_id);
CREATE INDEX idx_stress_calc_manifest_run_code ON investor_stress_calculation_manifest(calculation_run_code);
CREATE INDEX idx_stress_calc_manifest_investor ON investor_stress_calculation_manifest(investor_id);
CREATE INDEX idx_stress_calc_manifest_fingerprint ON investor_stress_calculation_manifest(snapshot_fingerprint);

-- Task 89 & 96 enhancements to existing tables
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS portfolio_id BIGINT;
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS stress_finding_id VARCHAR(100);

ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS thesis_status VARCHAR(50) DEFAULT 'ACTIVE_HYPOTHESIS';
ALTER TABLE investor_watchlist ADD COLUMN IF NOT EXISTS associated_finding_id VARCHAR(100);
