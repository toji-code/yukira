-- Flyway Migration V43: V1 Integrity Closure, Methodology Governance, Real-Data Readiness & Operational Dependencies
-- Authoritative Schema Authority for Tasks 310-359

-- 1. Implementation Ledger Version History (Task 315)
CREATE TABLE IF NOT EXISTS v1_implementation_ledger_version (
    id BIGSERIAL PRIMARY KEY,
    task_number INT NOT NULL,
    capability_id VARCHAR(100) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    change_reason TEXT NOT NULL,
    implementation_ref VARCHAR(255),
    author_actor VARCHAR(100) NOT NULL,
    superseded_record_ref VARCHAR(100),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_version_task ON v1_implementation_ledger_version(task_number);
CREATE INDEX IF NOT EXISTS idx_ledger_version_cap ON v1_implementation_ledger_version(capability_id);

-- 2. Metric Validation Evidence Registry (Task 317, 318)
CREATE TABLE IF NOT EXISTS metric_validation_evidence (
    id BIGSERIAL PRIMARY KEY,
    metric_code VARCHAR(50) NOT NULL,
    metric_version VARCHAR(20) NOT NULL,
    formula_version VARCHAR(20) NOT NULL,
    validation_date DATE NOT NULL,
    validation_scope VARCHAR(100) NOT NULL,
    reference_dataset_ref VARCHAR(255) NOT NULL,
    independent_methodology VARCHAR(255) NOT NULL,
    numerical_tolerance NUMERIC(15,10) NOT NULL,
    known_limitations TEXT,
    validation_result VARCHAR(50) NOT NULL,
    reviewer_identity VARCHAR(100) NOT NULL,
    authorization_status VARCHAR(50) NOT NULL,
    supersedes_id BIGINT REFERENCES metric_validation_evidence(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_validation_result CHECK (validation_result IN ('PASS', 'FAIL', 'INCONCLUSIVE')),
    CONSTRAINT chk_auth_status CHECK (authorization_status IN ('CANDIDATE', 'VALIDATED', 'APPROVED', 'RETIRED'))
);

CREATE INDEX IF NOT EXISTS idx_val_evidence_metric ON metric_validation_evidence(metric_code);

-- 3. Source Artifact Authority Tiers, Durability & Privacy (Task 327, 328, 329, 345)
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS source_authority_tier VARCHAR(50) DEFAULT 'OFFICIAL_PRIMARY';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS storage_state VARCHAR(50) DEFAULT 'VERIFIED_PERSISTED';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS is_investor_private BOOLEAN DEFAULT FALSE;
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS owning_investor_id BIGINT REFERENCES investor(id);

-- 4. Capital Market Calendar Versioning (Task 331)
CREATE TABLE IF NOT EXISTS capital_market_calendar_version (
    id BIGSERIAL PRIMARY KEY,
    exchange VARCHAR(50) NOT NULL,
    market_segment VARCHAR(50) NOT NULL,
    effective_year INT NOT NULL,
    calendar_version VARCHAR(50) NOT NULL,
    holiday_count INT NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    publication_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_calendar_ver UNIQUE (exchange, market_segment, effective_year, calendar_version)
);

-- 5. Extend Statutory Tax Classification Rules for Effective-Dated Legality (Task 336, 337, 340)
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS legal_instrument_ref VARCHAR(255);
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS finance_act_ref VARCHAR(100);
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS transfer_start_date DATE;
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS transfer_end_date DATE;
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS rule_version VARCHAR(50) DEFAULT '1.0';
ALTER TABLE mf_tax_classification_rule ADD COLUMN IF NOT EXISTS review_status VARCHAR(50) DEFAULT 'STATUTORY_BENCHMARKED';

-- 6. Operational Background Job Dependency & Provider Readiness (Task 357, 358)
CREATE TABLE IF NOT EXISTS operational_job_dependency (
    id BIGSERIAL PRIMARY KEY,
    job_identifier VARCHAR(100) NOT NULL UNIQUE,
    job_name VARCHAR(255) NOT NULL,
    job_purpose TEXT NOT NULL,
    execution_trigger VARCHAR(50) NOT NULL,
    schedule_cron VARCHAR(100),
    required_configuration_keys TEXT,
    external_provider_ref VARCHAR(100),
    provider_config_status VARCHAR(50) NOT NULL,
    last_status VARCHAR(50),
    last_executed_at TIMESTAMPTZ,
    last_failure_reason TEXT,
    is_operational BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_job_trigger CHECK (execution_trigger IN ('SCHEDULED_CRON', 'ON_DEMAND', 'EVENT_TRIGGERED')),
    CONSTRAINT chk_provider_config CHECK (provider_config_status IN ('CONFIGURED', 'MISSING_CREDENTIALS', 'MISSING_ENDPOINT', 'DISABLED', 'UNSUPPORTED'))
);

-- 7. Initial Seed Data for Governance Validation Records (Task 317)
INSERT INTO metric_validation_evidence (
    metric_code, metric_version, formula_version, validation_date, validation_scope,
    reference_dataset_ref, independent_methodology, numerical_tolerance,
    known_limitations, validation_result, reviewer_identity, authorization_status
) VALUES
(
    'RET-02', '1.0.0', '1.0', '2026-01-15', 'CANONICAL_PILOT_VERIFICATION',
    'AMFI Historical NAV Report 118955 (SHA-256: 900508f8...)', 'Deterministic Point-to-Point Terminal Ratio',
    0.0000000001, 'Requires exact terminal observation dates; no interpolation permitted.',
    'PASS', 'YUKIRA Quantitative Review Board', 'APPROVED'
),
(
    'RET-03', '1.0.0', '1.0', '2026-02-01', 'HISTORICAL_CAGR_REGIME_BENCHMARK',
    'AMFI Historical Multi-Year Pilot Series (2019-2024)', 'Annualized Geometric Mean Progression ((P_end/P_start)^(365.25/days) - 1)',
    0.00000001, 'Requires minimum 252 trading observations. Validated for historical CAGR horizons.',
    'PASS', 'YUKIRA Quantitative Review Board', 'VALIDATED'
),
(
    'RSK-01', '1.0.0', '1.0', '2026-02-01', 'ANNUALIZED_VOLATILITY_BENCHMARK',
    'Daily Log Return Sample Series (2023-2024)', 'Sample Standard Deviation of Log Returns * sqrt(252)',
    0.00000001, 'Annualization assumes 252 trading day convention. Sensitive to missing intermediate points.',
    'PASS', 'YUKIRA Quantitative Review Board', 'VALIDATED'
) ON CONFLICT DO NOTHING;

-- 8. Initial Seed Data for Capital Market Calendar Versions (Task 331)
INSERT INTO capital_market_calendar_version (
    exchange, market_segment, effective_year, calendar_version, holiday_count, source_reference, publication_date, is_active
) VALUES
('NSE', 'EQUITY', 2023, 'NSE-HOL-2023-V1', 15, 'NSE Circular Ref No: 12/2022', '2022-12-05', TRUE),
('NSE', 'EQUITY', 2024, 'NSE-HOL-2024-V1', 16, 'NSE Circular Ref No: 15/2023', '2023-12-08', TRUE),
('NSE', 'EQUITY', 2025, 'NSE-HOL-2025-V1', 15, 'NSE Circular Ref No: 18/2024', '2024-12-02', TRUE),
('NSE', 'EQUITY', 2026, 'NSE-HOL-2026-V1', 15, 'NSE Circular Ref No: 20/2025', '2025-12-01', TRUE)
ON CONFLICT DO NOTHING;

-- 9. Initial Seed Data for Operational Job Dependencies (Task 357, 358)
INSERT INTO operational_job_dependency (
    job_identifier, job_name, job_purpose, execution_trigger, schedule_cron,
    required_configuration_keys, external_provider_ref, provider_config_status, last_status, is_operational
) VALUES
(
    'JOB-AMFI-NAV-SYNC', 'AMFI Daily NAV Ingestion', 'Pulls latest daily NAV observations from AMFI portal',
    'SCHEDULED_CRON', '0 30 23 * * ?', 'amfi.portal.nav.url', 'AMFI_PORTAL', 'CONFIGURED', 'IDLE', TRUE
),
(
    'JOB-MONITORING-EVAL', 'Continuous Monitoring Evaluator', 'Evaluates active thesis invalidation conditions against point-in-time data',
    'SCHEDULED_CRON', '0 0 4 * * ?', 'yukira.monitoring.batch-size', 'INTERNAL_ENGINE', 'CONFIGURED', 'IDLE', TRUE
),
(
    'JOB-NOTIFICATION-DISPATCH', 'Notification Outbox Dispatcher', 'Dispatches queued notifications to third-party communication channels',
    'SCHEDULED_CRON', '0 */15 * * * ?', 'spring.mail.host, yukira.webhook.signing-secret', 'MULTI_CHANNEL', 'MISSING_CREDENTIALS', 'CONFIGURATION_REQUIRED', FALSE
),
(
    'JOB-BENCHMARK-SYNC', 'NSE TRI Benchmark Ingestion', 'Pulls Total Return Index benchmark observations',
    'ON_DEMAND', NULL, 'nse.data.api.key', 'NSE_HISTORICAL', 'MISSING_CREDENTIALS', 'CONFIGURATION_REQUIRED', FALSE
),
(
    'JOB-CALENDAR-REFRESH', 'Trading Calendar & Lag Auditor', 'Refreshes expected publication dates and identifies disclosure lags',
    'SCHEDULED_CRON', '0 0 6 * * ?', 'yukira.calendar.cache-ttl', 'INTERNAL_CALENDAR', 'CONFIGURED', 'IDLE', TRUE
) ON CONFLICT DO NOTHING;

-- 10. Update Tax Rules with Legal References (Task 336, 337)
UPDATE mf_tax_classification_rule
SET legal_instrument_ref = 'Income-tax Act, 1961 - Section 112A',
    finance_act_ref = 'Finance Act 2018 / Finance (No. 2) Act 2024',
    transfer_start_date = '2018-04-01',
    rule_version = '2024.1',
    review_status = 'STATUTORY_BENCHMARKED'
WHERE statutory_section = 'Section 112A' AND legal_instrument_ref IS NULL;

UPDATE mf_tax_classification_rule
SET legal_instrument_ref = 'Income-tax Act, 1961 - Section 111A',
    finance_act_ref = 'Finance Act 2004 / Finance (No. 2) Act 2024',
    transfer_start_date = '2004-10-01',
    rule_version = '2024.1',
    review_status = 'STATUTORY_BENCHMARKED'
WHERE statutory_section = 'Section 111A' AND legal_instrument_ref IS NULL;

UPDATE mf_tax_classification_rule
SET legal_instrument_ref = 'Income-tax Act, 1961 - Section 50AA',
    finance_act_ref = 'Finance Act 2023 (effective AY 2024-25)',
    transfer_start_date = '2023-04-01',
    rule_version = '2023.1',
    review_status = 'STATUTORY_BENCHMARKED'
WHERE statutory_section = 'Section 50AA' AND legal_instrument_ref IS NULL;

UPDATE mf_tax_classification_rule
SET legal_instrument_ref = 'Income-tax Act, 1961 - Section 112 (Grandfathered Debt)',
    finance_act_ref = 'Pre-Finance Act 2023 Regime (with CII Indexation)',
    transfer_start_date = '2000-04-01',
    rule_version = '2023.0',
    review_status = 'STATUTORY_BENCHMARKED'
WHERE statutory_section = 'Section 112' AND legal_instrument_ref IS NULL;
