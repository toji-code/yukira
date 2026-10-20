-- Phase 2U: Historical Point-in-Time AMFI Market-Cap Classification Ledger
-- Schema Authority: V28 Flyway Migration

CREATE TABLE security_market_cap_classification (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT REFERENCES security(id),
    isin VARCHAR(20) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    bse_symbol VARCHAR(50),
    nse_symbol VARCHAR(50),
    market_cap_rank INTEGER NOT NULL,
    market_cap_category VARCHAR(30) NOT NULL,
    avg_market_cap_crores NUMERIC(24, 4),
    ranking_period VARCHAR(50) NOT NULL,
    ranking_period_end_date DATE NOT NULL,
    source_publication_date DATE NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    availability_time TIMESTAMPTZ NOT NULL,
    ingestion_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    revision_seq INTEGER NOT NULL DEFAULT 1,
    is_latest_revision BOOLEAN NOT NULL DEFAULT TRUE,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'VALIDATED',
    mapping_status VARCHAR(30) NOT NULL DEFAULT 'MAPPED',
    CONSTRAINT uq_sec_mcap_pit UNIQUE (isin, ranking_period_end_date, revision_seq),
    CONSTRAINT chk_mcap_category CHECK (market_cap_category IN ('LARGE_CAP', 'MID_CAP', 'SMALL_CAP', 'UNCLASSIFIED')),
    CONSTRAINT chk_mcap_validation CHECK (validation_status IN ('VALIDATED', 'SUSPICIOUS', 'REJECTED')),
    CONSTRAINT chk_mcap_mapping CHECK (mapping_status IN ('MAPPED', 'UNMAPPED', 'AMBIGUOUS'))
);

-- Point-in-Time query performance indexes
CREATE INDEX idx_sec_mcap_pit ON security_market_cap_classification (
    isin, effective_from, availability_time, revision_seq DESC
);

CREATE INDEX idx_sec_mcap_sec_pit ON security_market_cap_classification (
    security_id, effective_from, availability_time, revision_seq DESC
);

CREATE INDEX idx_sec_mcap_period ON security_market_cap_classification (
    ranking_period_end_date, market_cap_rank
);

CREATE INDEX idx_sec_mcap_artifact ON security_market_cap_classification (
    source_artifact_id
);
