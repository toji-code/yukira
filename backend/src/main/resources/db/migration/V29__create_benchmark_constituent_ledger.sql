-- Phase 2V: Historical Point-in-Time Benchmark Constituent Ledger
-- Schema Authority: V29 Flyway Migration

CREATE TABLE benchmark_constituent (
    id BIGSERIAL PRIMARY KEY,
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    security_id BIGINT REFERENCES security(id),
    isin VARCHAR(20) NOT NULL,
    symbol VARCHAR(50),
    company_name VARCHAR(255) NOT NULL,
    industry VARCHAR(100),
    weight NUMERIC(10, 6),
    weight_unit VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    is_member BOOLEAN NOT NULL DEFAULT TRUE,
    effective_date DATE NOT NULL,
    availability_time TIMESTAMPTZ NOT NULL,
    ingestion_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    revision_seq INTEGER NOT NULL DEFAULT 1,
    is_latest_revision BOOLEAN NOT NULL DEFAULT TRUE,
    mapping_status VARCHAR(30) NOT NULL DEFAULT 'MAPPED',
    validation_status VARCHAR(30) NOT NULL DEFAULT 'VALIDATED',
    CONSTRAINT uq_bm_constituent_pit UNIQUE (benchmark_id, isin, effective_date, revision_seq),
    CONSTRAINT chk_bm_mapping_status CHECK (mapping_status IN ('MAPPED', 'UNMAPPED', 'AMBIGUOUS')),
    CONSTRAINT chk_bm_validation_status CHECK (validation_status IN ('VALIDATED', 'PARTIAL_DISCLOSURE', 'SUSPICIOUS', 'REJECTED'))
);

-- Point-in-Time query performance indexes
CREATE INDEX idx_bm_constituent_pit ON benchmark_constituent (
    benchmark_id, effective_date, availability_time, revision_seq DESC
);

CREATE INDEX idx_bm_constituent_sec_pit ON benchmark_constituent (
    security_id, effective_date, availability_time, revision_seq DESC
);

CREATE INDEX idx_bm_constituent_isin ON benchmark_constituent (
    isin
);

CREATE INDEX idx_bm_constituent_artifact ON benchmark_constituent (
    source_artifact_id
);
