-- Flyway Migration V35: Monitoring Execution Ledger, Authoritative Metric Alignment & Evidence Integrity
-- Schema authority for scheduled monitoring executions, execution telemetry, and point-in-time provenance.

CREATE TABLE investor_monitoring_execution (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT REFERENCES investor(id) ON DELETE CASCADE,
    execution_trigger VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    execution_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE,
    funds_evaluated_count INTEGER NOT NULL DEFAULT 0,
    portfolio_scopes_evaluated_count INTEGER NOT NULL DEFAULT 0,
    conditions_evaluated_count INTEGER NOT NULL DEFAULT 0,
    events_generated_count INTEGER NOT NULL DEFAULT 0,
    events_deduplicated_count INTEGER NOT NULL DEFAULT 0,
    unavailable_evidence_count INTEGER NOT NULL DEFAULT 0,
    stale_source_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    summary_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_exec_investor ON investor_monitoring_execution(investor_id, started_at DESC);
CREATE INDEX idx_monitoring_exec_status ON investor_monitoring_execution(execution_status);

-- Extend investor_monitoring_condition with evidence state, units, time window, and trigger history
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS evidence_state VARCHAR(32) DEFAULT 'AVAILABLE';
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS threshold_classification VARCHAR(32) DEFAULT 'INVESTOR_DEFINED_THRESHOLD';
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS threshold_unit VARCHAR(32);
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS time_window VARCHAR(32);
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS trigger_count INTEGER DEFAULT 0;
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS last_triggered_at TIMESTAMP WITH TIME ZONE;

-- Extend investor_monitoring_event with point-in-time evidence provenance and execution attribution
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS threshold_classification VARCHAR(32) DEFAULT 'SYSTEM_CANDIDATE_THRESHOLD';
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS evidence_state VARCHAR(32) DEFAULT 'AVAILABLE';
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS availability_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS ingestion_timestamp TIMESTAMP WITH TIME ZONE;
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS source_revision VARCHAR(64);
ALTER TABLE investor_monitoring_event ADD COLUMN IF NOT EXISTS execution_id BIGINT REFERENCES investor_monitoring_execution(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_monitoring_event_exec ON investor_monitoring_event(execution_id);

