-- Phase 2P: Stage 2 - Risk-Free Rate Observation Ledger (FBIL 91-Day T-Bill)

CREATE TABLE risk_free_observation (
    id BIGSERIAL PRIMARY KEY,
    benchmark_code VARCHAR(50) NOT NULL DEFAULT 'FBIL_91D_TBILL',
    effective_date DATE NOT NULL,
    quoted_yield NUMERIC(12, 8) NOT NULL,
    daycount_convention VARCHAR(30) NOT NULL DEFAULT 'ACT_365',
    revision_seq INTEGER NOT NULL DEFAULT 1,
    is_latest_revision BOOLEAN NOT NULL DEFAULT TRUE,
    availability_time TIMESTAMPTZ NOT NULL,
    ingestion_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    quality_assessment VARCHAR(30) NOT NULL DEFAULT 'VALID',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'VERIFIED',
    revision_status VARCHAR(30) NOT NULL DEFAULT 'ORIGINAL',
    temporal_status VARCHAR(30) NOT NULL DEFAULT 'CURRENT',
    presence_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    integrity_condition VARCHAR(30) NOT NULL DEFAULT 'NONE',
    CONSTRAINT uq_risk_free_observation UNIQUE (benchmark_code, effective_date, revision_seq)
);

-- Point-in-Time Index for historical PIT queries
CREATE INDEX idx_rf_obs_pit ON risk_free_observation (
    benchmark_code, effective_date, availability_time, revision_seq DESC
);

-- Index for fast latest-revision lookups
CREATE INDEX idx_rf_obs_latest ON risk_free_observation (
    benchmark_code, effective_date
) WHERE is_latest_revision = TRUE;

-- Add risk-free observation foreign key to calculation_run_input_observation
ALTER TABLE calculation_run_input_observation
    ADD COLUMN risk_free_observation_id BIGINT REFERENCES risk_free_observation(id);

-- Update check constraint to ensure exactly one observation reference per input record
ALTER TABLE calculation_run_input_observation
    DROP CONSTRAINT chk_run_input_exactly_one_fk;

ALTER TABLE calculation_run_input_observation
    ADD CONSTRAINT chk_run_input_exactly_one_fk CHECK (
        (nav_observation_id IS NOT NULL AND benchmark_observation_id IS NULL AND risk_free_observation_id IS NULL) OR
        (nav_observation_id IS NULL AND benchmark_observation_id IS NOT NULL AND risk_free_observation_id IS NULL) OR
        (nav_observation_id IS NULL AND benchmark_observation_id IS NULL AND risk_free_observation_id IS NOT NULL)
    );

-- Unique constraint for calculation_run and risk_free_observation
ALTER TABLE calculation_run_input_observation
    ADD CONSTRAINT uq_run_input_risk_free UNIQUE (calculation_run_id, risk_free_observation_id);

CREATE INDEX idx_run_input_obs_rf ON calculation_run_input_observation (
    risk_free_observation_id
);
