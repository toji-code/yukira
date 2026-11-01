-- Flyway Migration V40: Continuous Monitoring, Notifications, Portfolio Intelligence & V1 Product Models
-- Schema authority for Tasks 160–209 in Project YUKIRA.

-- 1. Extend investor_monitoring_condition with scheduling cadence, due dates, idempotency, and outcome states
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS evaluation_cadence VARCHAR(32) DEFAULT 'DAILY';
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS next_evaluation_due_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS last_withholding_reason VARCHAR(64);
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS last_outcome VARCHAR(32) DEFAULT 'NOT_EVALUATED';
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS last_successful_evaluated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS last_failed_evaluated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_monitoring_cond_due 
ON investor_monitoring_condition(is_enabled, condition_status, next_evaluation_due_at);

-- 2. Extend investor_monitoring_execution with idempotency and withholding breakdowns
ALTER TABLE investor_monitoring_execution ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE investor_monitoring_execution ADD COLUMN IF NOT EXISTS withholding_reasons_json TEXT;

-- 3. Monitoring Condition Evaluation Execution History (Task 163)
CREATE TABLE IF NOT EXISTS investor_monitoring_condition_history (
    id BIGSERIAL PRIMARY KEY,
    condition_id BIGINT NOT NULL REFERENCES investor_monitoring_condition(id) ON DELETE CASCADE,
    execution_id BIGINT REFERENCES investor_monitoring_execution(id) ON DELETE SET NULL,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    observation_cutoff DATE,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE,
    evaluation_outcome VARCHAR(64) NOT NULL,
    trigger_status VARCHAR(32) NOT NULL,
    withholding_reason VARCHAR(64),
    evaluated_value VARCHAR(255),
    threshold_value VARCHAR(64),
    source_context VARCHAR(255),
    event_id BIGINT REFERENCES investor_monitoring_event(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_monitoring_cond_hist_cond 
ON investor_monitoring_condition_history(condition_id, evaluated_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitoring_cond_hist_investor 
ON investor_monitoring_condition_history(investor_id, evaluated_at DESC);

-- 4. Persistent Notification Outbox (Task 165)
CREATE TABLE IF NOT EXISTS investor_notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES investor_monitoring_event(id) ON DELETE SET NULL,
    notification_type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient_reference VARCHAR(255) NOT NULL,
    payload_title VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    idempotency_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notif_outbox_status_attempt 
ON investor_notification_outbox(delivery_status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_notif_outbox_investor 
ON investor_notification_outbox(investor_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_notif_outbox_dedup 
ON investor_notification_outbox(investor_id, idempotency_hash) WHERE idempotency_hash IS NOT NULL;

-- 5. Investor Notification Preferences (Task 166)
CREATE TABLE IF NOT EXISTS investor_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL UNIQUE REFERENCES investor(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_address VARCHAR(255),
    monitoring_triggers_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    research_updates_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    data_quality_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Investor Webhook Configuration (Tasks 168, 169)
CREATE TABLE IF NOT EXISTS investor_webhook_config (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    endpoint_url VARCHAR(1024) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    subscription_events_csv VARCHAR(255) NOT NULL DEFAULT 'MONITORING_TRIGGER,CONDITION_STATUS_CHANGE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failure_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_config_investor 
ON investor_webhook_config(investor_id);

-- 7. In-App Notification Center (Task 170)
CREATE TABLE IF NOT EXISTS investor_in_app_notification (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES investor_monitoring_event(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'INFO',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    action_url VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_in_app_notif_investor_read 
ON investor_in_app_notification(investor_id, is_read, created_at DESC);

-- 8. Scheme-Specific Benchmark Mapping Registry (Task 172)
CREATE TABLE IF NOT EXISTS scheme_benchmark_mapping (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id) ON DELETE CASCADE,
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id) ON DELETE CASCADE,
    mapping_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
    mapping_source VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_tri BOOLEAN NOT NULL DEFAULT TRUE,
    provenance_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheme_bm_map_scheme 
ON scheme_benchmark_mapping(scheme_id, effective_from);

-- 9. Scheme Data Coverage Registry (Task 175)
CREATE TABLE IF NOT EXISTS scheme_data_coverage (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL UNIQUE REFERENCES scheme(id) ON DELETE CASCADE,
    nav_coverage_status VARCHAR(32) NOT NULL DEFAULT 'SUPPORTED',
    benchmark_coverage_status VARCHAR(32) NOT NULL DEFAULT 'SUPPORTED',
    holdings_coverage_status VARCHAR(32) NOT NULL DEFAULT 'INCOMPLETE',
    risk_free_coverage_status VARCHAR(32) NOT NULL DEFAULT 'SUPPORTED',
    peer_universe_status VARCHAR(32) NOT NULL DEFAULT 'SUPPORTED',
    earliest_nav_date DATE,
    latest_nav_date DATE,
    latest_holdings_period DATE,
    latest_nav_availability TIMESTAMP WITH TIME ZONE,
    data_limitations_summary TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Holdings Artifact Reconciliation & Asset Classification (Tasks 177, 178, 179)
CREATE TABLE IF NOT EXISTS holdings_artifact_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    amc_id BIGINT REFERENCES amc(id),
    reporting_period DATE NOT NULL,
    parser_version VARCHAR(32) NOT NULL,
    parsing_outcome VARCHAR(32) NOT NULL,
    total_rows_read INTEGER NOT NULL DEFAULT 0,
    equity_rows_parsed INTEGER NOT NULL DEFAULT 0,
    debt_rows_parsed INTEGER NOT NULL DEFAULT 0,
    cash_rows_parsed INTEGER NOT NULL DEFAULT 0,
    derivative_rows_parsed INTEGER NOT NULL DEFAULT 0,
    unclassified_rows INTEGER NOT NULL DEFAULT 0,
    total_market_value NUMERIC(19, 4),
    derivative_notional_value NUMERIC(19, 4),
    cash_market_value NUMERIC(19, 4),
    error_diagnostics TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_holdings_recon_period 
ON holdings_artifact_reconciliation(amc_id, reporting_period);

-- 11. Scheme Lifecycle and Merger Continuity (Task 182)
CREATE TABLE IF NOT EXISTS scheme_lifecycle_record (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id) ON DELETE CASCADE,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    effective_date DATE NOT NULL,
    successor_scheme_id BIGINT REFERENCES scheme(id),
    predecessor_scheme_id BIGINT REFERENCES scheme(id),
    regulatory_circular_ref VARCHAR(255),
    remarks TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheme_lifecycle_scheme 
ON scheme_lifecycle_record(scheme_id, effective_date);

-- 12. Extend investor_transaction with detailed classification and corporate actions (Tasks 183, 184)
ALTER TABLE investor_transaction ADD COLUMN IF NOT EXISTS classification VARCHAR(32) DEFAULT 'EXTERNAL_INVESTMENT';
ALTER TABLE investor_transaction ADD COLUMN IF NOT EXISTS corporate_action_type VARCHAR(32);
ALTER TABLE investor_transaction ADD COLUMN IF NOT EXISTS corporate_action_ratio VARCHAR(32);
ALTER TABLE investor_transaction ADD COLUMN IF NOT EXISTS source_record_reference VARCHAR(255);

-- 13. Portfolio Historical Valuation History (Task 185)
CREATE TABLE IF NOT EXISTS portfolio_valuation_history (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    portfolio_id BIGINT,
    valuation_date DATE NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE,
    total_market_value NUMERIC(19, 4) NOT NULL,
    total_cost_basis NUMERIC(19, 4) NOT NULL,
    unrealized_gain_loss NUMERIC(19, 4),
    valued_positions_count INTEGER NOT NULL DEFAULT 0,
    unvalued_positions_count INTEGER NOT NULL DEFAULT 0,
    valuation_source_context TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_port_val_hist_investor 
ON portfolio_valuation_history(investor_id, valuation_date DESC);

-- 14. Investor Portfolio Research Snapshots (Task 190)
CREATE TABLE IF NOT EXISTS investor_portfolio_research_snapshot (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    snapshot_title VARCHAR(255) NOT NULL,
    allocation_snapshot_id BIGINT REFERENCES investor_portfolio_allocation_snapshot(id),
    as_of_date DATE NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    xirr_value NUMERIC(19, 6),
    total_value NUMERIC(19, 4),
    cash_flow_summary_json TEXT,
    research_thesis TEXT,
    investigation_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_port_res_snap_investor 
ON investor_portfolio_research_snapshot(investor_id, as_of_date DESC);

-- 15. Investor Research Tags (Task 192)
CREATE TABLE IF NOT EXISTS investor_research_tag (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    tag_name VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_research_tag_investor 
ON investor_research_tag(investor_id, tag_name);
CREATE INDEX IF NOT EXISTS idx_research_tag_target 
ON investor_research_tag(investor_id, target_type, target_id);

-- 16. Investor Saved Discovery Filters (Task 193)
CREATE TABLE IF NOT EXISTS investor_saved_filter (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    filter_name VARCHAR(128) NOT NULL,
    filter_criteria_json TEXT NOT NULL,
    filter_type VARCHAR(32) NOT NULL DEFAULT 'DISCOVERY',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_saved_filter_investor 
ON investor_saved_filter(investor_id, created_at DESC);

-- 17. Saved Comparison Research Versioning (Task 194)
CREATE TABLE IF NOT EXISTS investor_comparison_research_version (
    id BIGSERIAL PRIMARY KEY,
    comparison_research_id BIGINT NOT NULL REFERENCES investor_comparison_research(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    scheme_option_ids_csv VARCHAR(255) NOT NULL,
    as_of_date DATE NOT NULL,
    benchmark_code VARCHAR(64),
    compatibility_status VARCHAR(64) NOT NULL,
    metrics_summary_json TEXT,
    investor_thesis TEXT,
    investor_notes TEXT,
    version_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comp_res_ver_parent 
ON investor_comparison_research_version(comparison_research_id, version_number DESC);

-- 18. Research Investigation Actions (Task 195)
CREATE TABLE IF NOT EXISTS investor_investigation_action (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    action_title VARCHAR(255) NOT NULL,
    action_description TEXT,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    due_date DATE,
    completed_at TIMESTAMP WITH TIME ZONE,
    outcome_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_investigation_action_investor 
ON investor_investigation_action(investor_id, status);

-- 19. Evidence-Referenced AI Interpretation Versioning (Task 199)
CREATE TABLE IF NOT EXISTS investor_ai_interpretation_version (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT REFERENCES investor(id) ON DELETE SET NULL,
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    evidence_packet_fingerprint VARCHAR(128) NOT NULL,
    prompt_template_version VARCHAR(32) NOT NULL,
    model_provider VARCHAR(64) NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    as_of_date DATE NOT NULL,
    abstention_state VARCHAR(32) NOT NULL DEFAULT 'NONE',
    abstention_reason VARCHAR(255),
    grounded_interpretation_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_interp_ver_option 
ON investor_ai_interpretation_version(scheme_option_id, as_of_date DESC);

-- 20. Investor Display Preferences (Task 203)
CREATE TABLE IF NOT EXISTS investor_display_preference (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL UNIQUE REFERENCES investor(id) ON DELETE CASCADE,
    default_analysis_period VARCHAR(16) NOT NULL DEFAULT '3Y',
    default_workspace_tab VARCHAR(32) NOT NULL DEFAULT 'OVERVIEW',
    preferred_theme VARCHAR(16) NOT NULL DEFAULT 'DARK',
    show_candidate_metric_warnings BOOLEAN NOT NULL DEFAULT TRUE,
    date_display_format VARCHAR(32) NOT NULL DEFAULT 'YYYY-MM-DD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 21. Backend Job Execution and Operational Status Persistence (Task 207)
CREATE TABLE IF NOT EXISTS operational_job_execution (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(64) NOT NULL,
    execution_trigger VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    execution_status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    scope_description VARCHAR(255),
    records_processed INTEGER NOT NULL DEFAULT 0,
    records_failed INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    telemetry_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_operational_job_name_time 
ON operational_job_execution(job_name, started_at DESC);

