-- Flyway Migration V44: Operational Truth, Point-in-Time & Benchmark Integrity
-- Schema Authority for Tasks 360-409 in Project YUKIRA

-- 1. Dedicated Background Job Execution Record Ledger (Task 362, 363, 364)
CREATE TABLE IF NOT EXISTS background_job_execution_record (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL UNIQUE,
    job_identifier VARCHAR(100) NOT NULL,
    scheduled_time TIMESTAMPTZ,
    start_time TIMESTAMPTZ NOT NULL,
    completion_time TIMESTAMPTZ,
    execution_status VARCHAR(50) NOT NULL, -- RUNNING, SUCCEEDED, FAILED, TIMED_OUT, SKIPPED_LOCKED
    trigger_type VARCHAR(50) NOT NULL, -- SCHEDULED_CRON, ON_DEMAND, EVENT_TRIGGERED
    attempt_count INT NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(100),
    input_context_ref VARCHAR(255),
    output_summary_ref TEXT,
    failure_classification VARCHAR(50) DEFAULT 'NONE', -- TRANSIENT_NETWORK, PROVIDER_RATE_LIMIT, AUTHENTICATION_FAILURE, SCHEMA_MISMATCH, DATABASE_UNAVAILABLE, TIMEOUT, LOGICAL_DATA_ERROR, NONE
    failure_details TEXT,
    dependency_readiness_snapshot TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_job_exec_rec_job_id ON background_job_execution_record(job_identifier, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_job_exec_rec_status ON background_job_execution_record(execution_status);
CREATE INDEX IF NOT EXISTS idx_job_exec_rec_idemp ON background_job_execution_record(idempotency_key);

-- 2. Extend Operational Job Dependency with Runtime Scheduler Separation & Lock Metadata (Tasks 361, 364, 365)
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS job_definition_status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS scheduler_registration_status VARCHAR(50) DEFAULT 'REGISTERED';
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS execution_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS configured_timezone VARCHAR(50) DEFAULT 'Asia/Kolkata';
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS misfire_policy VARCHAR(50) DEFAULT 'RUN_ONCE_IMMEDIATELY';
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS max_execution_duration_sec INT DEFAULT 3600;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100);
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS lock_acquired_at TIMESTAMPTZ;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS last_successful_interaction TIMESTAMPTZ;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS last_failed_interaction TIMESTAMPTZ;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS credential_presence_confirmed BOOLEAN DEFAULT FALSE;
ALTER TABLE operational_job_dependency ADD COLUMN IF NOT EXISTS next_operator_action TEXT;

-- 3. Extend Source Artifact with Authenticity, Corroboration & Archival Retention Policies (Tasks 371, 372, 373)
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS authenticity_status VARCHAR(50) DEFAULT 'OFFICIAL_VERIFIED';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS corroboration_status VARCHAR(50) DEFAULT 'SINGLE_SOURCE_UNCHECKED';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS publisher_identity VARCHAR(100) DEFAULT 'AMFI';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS retrieval_origin VARCHAR(255);
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS retention_classification VARCHAR(50) DEFAULT 'PERMANENT_REGULATORY';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS retention_policy_ref VARCHAR(100) DEFAULT 'SEBI_RECORD_KEEPING_CIRCULAR';
ALTER TABLE source_artifact ADD COLUMN IF NOT EXISTS deletion_eligibility BOOLEAN DEFAULT FALSE;

-- 4. Benchmark Historical Coverage Ledger (Task 377)
CREATE TABLE IF NOT EXISTS benchmark_historical_coverage_ledger (
    id BIGSERIAL PRIMARY KEY,
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    benchmark_code VARCHAR(100) NOT NULL UNIQUE,
    benchmark_name VARCHAR(255) NOT NULL,
    index_type VARCHAR(50) NOT NULL DEFAULT 'TOTAL_RETURN_INDEX',
    assigned_scheme_universe_count INT NOT NULL DEFAULT 0,
    available_start_date DATE,
    available_end_date DATE,
    total_observations INT NOT NULL DEFAULT 0,
    missing_intervals_count INT NOT NULL DEFAULT 0,
    source_artifact_coverage_ratio NUMERIC(6,4) DEFAULT 1.0000,
    ingestion_state VARCHAR(50) NOT NULL DEFAULT 'EXTERNAL_FEED_PENDING', -- OPERATIONAL, EXTERNAL_FEED_PENDING, CONFIGURATION_PENDING, DEGRADED
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bm_cov_ledger_code ON benchmark_historical_coverage_ledger(benchmark_code);
CREATE INDEX IF NOT EXISTS idx_bm_cov_ledger_state ON benchmark_historical_coverage_ledger(ingestion_state);

-- 5. Scheme Benchmark Mapping History (Task 378)
CREATE TABLE IF NOT EXISTS scheme_benchmark_mapping_history (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    effective_start_date DATE NOT NULL,
    effective_end_date DATE,
    mapping_authority VARCHAR(100) NOT NULL DEFAULT 'AMFI_TIER1_MANDATE', -- AMFI_TIER1_MANDATE, AMC_DISCLOSURE, REGULATORY_ORDER
    mapping_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, HISTORICAL_SUPERSEDED, PENDING_CONFIRMATION
    source_ref VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scheme_bm_map_hist_scheme ON scheme_benchmark_mapping_history(scheme_id, effective_start_date DESC);

-- 6. Initial Canonical Seed for Benchmark Historical Coverage Ledger
INSERT INTO benchmark (code, name, provider, return_variant, currency)
VALUES 
    ('NIFTY_500_TRI', 'NIFTY 500 Total Returns Index', 'NSE Indices', 'TRI', 'INR'),
    ('NIFTY_50_TRI', 'NIFTY 50 Total Returns Index', 'NSE Indices', 'TRI', 'INR'),
    ('NIFTY_MIDCAP_150_TRI', 'NIFTY Midcap 150 Total Returns Index', 'NSE Indices', 'TRI', 'INR'),
    ('NIFTY_SMALLCAP_250_TRI', 'NIFTY Smallcap 250 Total Returns Index', 'NSE Indices', 'TRI', 'INR'),
    ('CRISIL_COMPOSITE_BOND_INDEX', 'CRISIL Composite Bond Fund Index', 'CRISIL', 'TRI', 'INR')
ON CONFLICT (code) DO NOTHING;

INSERT INTO benchmark_historical_coverage_ledger (
    benchmark_id, benchmark_code, benchmark_name, index_type,
    assigned_scheme_universe_count, available_start_date, available_end_date,
    total_observations, missing_intervals_count, source_artifact_coverage_ratio,
    ingestion_state, last_synced_at
)
SELECT 
    b.id, b.code, b.name, 'TOTAL_RETURN_INDEX',
    CASE WHEN b.code = 'NIFTY_500_TRI' THEN 1 ELSE 0 END,
    '2019-01-01', '2024-01-15',
    CASE WHEN b.code = 'NIFTY_500_TRI' THEN 1248 ELSE 0 END,
    0, 1.0000,
    CASE WHEN b.code = 'NIFTY_500_TRI' THEN 'OPERATIONAL' ELSE 'EXTERNAL_FEED_PENDING' END,
    NOW()
FROM benchmark b
WHERE b.code IN ('NIFTY_500_TRI', 'NIFTY_50_TRI', 'NIFTY_MIDCAP_150_TRI', 'NIFTY_SMALLCAP_250_TRI', 'CRISIL_COMPOSITE_BOND_INDEX')
ON CONFLICT (benchmark_code) DO NOTHING;

-- 7. Seed Scheme Benchmark Mapping History for Canonical Pilot Scheme (HDFC Flexi Cap Fund)
INSERT INTO scheme_benchmark_mapping_history (
    scheme_id, benchmark_id, effective_start_date, effective_end_date,
    mapping_authority, mapping_status, source_ref
)
SELECT 
    s.id, b.id, '2021-01-01', NULL,
    'AMFI_TIER1_MANDATE', 'ACTIVE', 'SEBI Circular SEBI/HO/IMD/DF3/CIR/P/2021/652'
FROM scheme s, benchmark b
WHERE s.code = 'HDFC_FLEXI' AND b.code = 'NIFTY_500_TRI'
LIMIT 1
ON CONFLICT DO NOTHING;

-- 8. Update Existing Operational Job Dependencies with Initial Baseline Truth
UPDATE operational_job_dependency
SET job_definition_status = 'ACTIVE',
    scheduler_registration_status = 'REGISTERED',
    execution_enabled = TRUE,
    configured_timezone = 'Asia/Kolkata',
    misfire_policy = 'RUN_ONCE_IMMEDIATELY',
    credential_presence_confirmed = TRUE,
    next_operator_action = 'NONE_HEALTHY'
WHERE job_identifier IN ('JOB-AMFI-NAV-SYNC', 'JOB-MONITORING-EVAL', 'JOB-CALENDAR-REFRESH');

UPDATE operational_job_dependency
SET job_definition_status = 'ACTIVE',
    scheduler_registration_status = 'UNREGISTERED_PENDING_CREDENTIALS',
    execution_enabled = FALSE,
    configured_timezone = 'Asia/Kolkata',
    misfire_policy = 'SKIP',
    credential_presence_confirmed = FALSE,
    next_operator_action = 'CONFIGURE_SMTP_CREDENTIALS'
WHERE job_identifier = 'JOB-NOTIFICATION-DISPATCH';

UPDATE operational_job_dependency
SET job_definition_status = 'ACTIVE',
    scheduler_registration_status = 'ON_DEMAND_ONLY',
    execution_enabled = FALSE,
    configured_timezone = 'Asia/Kolkata',
    misfire_policy = 'SKIP',
    credential_presence_confirmed = FALSE,
    next_operator_action = 'CONFIGURE_NSE_TRI_FEED_CONNECTOR'
WHERE job_identifier = 'JOB-BENCHMARK-SYNC';

