-- Flyway Migration V42: V1 Reconciliation, Quantitative Integrity, Tax Rules & Monitoring Outbox
-- Authoritative schema additions for Tasks 260–309 in Project YUKIRA.

-- 1. Extend Source Artifact with Location, Covered Date Range and Storage Backend (Task 266)
ALTER TABLE source_artifact
    ADD COLUMN IF NOT EXISTS source_location_ref VARCHAR(512),
    ADD COLUMN IF NOT EXISTS publication_date DATE,
    ADD COLUMN IF NOT EXISTS effective_start_date DATE,
    ADD COLUMN IF NOT EXISTS effective_end_date DATE,
    ADD COLUMN IF NOT EXISTS ingestion_status VARCHAR(64) NOT NULL DEFAULT 'INGESTED',
    ADD COLUMN IF NOT EXISTS storage_backend VARCHAR(32) NOT NULL DEFAULT 'DATABASE_BLOB';

CREATE INDEX IF NOT EXISTS idx_source_artifact_range 
ON source_artifact(effective_start_date, effective_end_date);

-- 2. Indian Mutual Fund Statutory Capital Gains Tax Classification Rules (Tasks 245, 299)
CREATE TABLE IF NOT EXISTS mf_tax_classification_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    category_pattern VARCHAR(128) NOT NULL,
    statutory_section VARCHAR(64) NOT NULL,
    acquisition_start_date DATE NOT NULL,
    acquisition_end_date DATE,
    stcg_threshold_days INTEGER NOT NULL DEFAULT 365,
    equity_exposure_min_percent NUMERIC(5,2),
    stcg_tax_rate_percent NUMERIC(5,2),
    ltcg_tax_rate_percent NUMERIC(5,2),
    indexation_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    provisional_notice TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mf_tax_rule_code 
ON mf_tax_classification_rule(rule_code);

-- Seed authoritative Indian Mutual Fund statutory tax classification rules
INSERT INTO mf_tax_classification_rule (
    rule_code,
    category_pattern,
    statutory_section,
    acquisition_start_date,
    acquisition_end_date,
    stcg_threshold_days,
    equity_exposure_min_percent,
    stcg_tax_rate_percent,
    ltcg_tax_rate_percent,
    indexation_allowed,
    provisional_notice
) VALUES 
(
    'EQUITY_ORIENTED_STANDARD',
    'Equity',
    'Section 112A / 111A',
    '2018-04-01',
    NULL,
    365,
    65.00,
    20.00,
    12.50,
    FALSE,
    'Provisional research estimate only. Subject to Section 112A/111A rules and statutory exemption limits under the Income-tax Act, 1961. Consult a qualified chartered accountant before filing returns.'
),
(
    'SPECIFIED_DEBT_SEC_50AA',
    'Debt',
    'Section 50AA',
    '2023-04-01',
    NULL,
    0,
    0.00,
    NULL,
    NULL,
    FALSE,
    'Under Section 50AA (introduced via Finance Act 2023), capital gains on specified mutual funds (<=35% equity) acquired on or after 1 April 2023 are deemed short-term capital gains and taxed at applicable investor slab rates regardless of holding period.'
),
(
    'LEGACY_DEBT_INDEXATION_SEC_112',
    'Debt',
    'Section 112 (Grandfathered)',
    '1999-04-01',
    '2023-03-31',
    1095,
    0.00,
    NULL,
    20.00,
    TRUE,
    'Grandfathered debt mutual fund units acquired prior to 1 April 2023. Holding periods >= 36 months (1095 days) qualify for LTCG at 20% with Cost Inflation Index (CII) indexation benefits under Section 112.'
)
ON CONFLICT (rule_code) DO NOTHING;

-- 3. Versioned Investor Research Thesis Ledger (Task 308)
CREATE TABLE IF NOT EXISTS investor_research_thesis_version (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_id BIGINT REFERENCES scheme(id) ON DELETE CASCADE,
    scheme_option_id BIGINT REFERENCES scheme_option(id) ON DELETE CASCADE,
    watchlist_id BIGINT REFERENCES investor_watchlist(id) ON DELETE SET NULL,
    thesis_id VARCHAR(64) NOT NULL,
    version_number INTEGER NOT NULL DEFAULT 1,
    author VARCHAR(128) NOT NULL DEFAULT 'Investor',
    thesis_title VARCHAR(255) NOT NULL,
    thesis_body TEXT NOT NULL,
    linked_condition VARCHAR(255),
    linked_evidence TEXT,
    revision_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_investor_thesis_version UNIQUE (investor_id, thesis_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_thesis_version_lookup 
ON investor_research_thesis_version(investor_id, thesis_id);

-- 4. Continuous Monitoring Evaluation Run Ledger (Task 304)
CREATE TABLE IF NOT EXISTS monitoring_evaluation_run (
    id BIGSERIAL PRIMARY KEY,
    evaluation_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_cutoff DATE NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    calculation_run_id BIGINT REFERENCES calculation_run(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    triggers_evaluated INTEGER NOT NULL DEFAULT 0,
    alerts_generated INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL DEFAULT 'COMPLETED',
    execution_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mon_eval_run_cutoffs 
ON monitoring_evaluation_run(data_cutoff, status);

-- 5. Monitoring Notification Delivery Outbox Ledger (Tasks 305, 306)
CREATE TABLE IF NOT EXISTS monitoring_notification_delivery (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL REFERENCES investor_monitoring_event(id) ON DELETE CASCADE,
    channel VARCHAR(64) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    delivery_status VARCHAR(64) NOT NULL DEFAULT 'QUEUED',
    attempted_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mon_notif_status 
ON monitoring_notification_delivery(delivery_status, channel);

-- 6. Source Availability Calendar Execution Log (Task 274)
CREATE TABLE IF NOT EXISTS source_calendar_execution_log (
    id BIGSERIAL PRIMARY KEY,
    calendar_id BIGINT REFERENCES source_publication_calendar(id) ON DELETE CASCADE,
    source_name VARCHAR(64) NOT NULL,
    scheduled_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_ingested_at TIMESTAMP WITH TIME ZONE,
    delay_minutes INTEGER,
    status VARCHAR(64) NOT NULL DEFAULT 'ON_TIME',
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_src_cal_exec_status 
ON source_calendar_execution_log(source_name, status);
