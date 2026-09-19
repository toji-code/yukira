-- Phase 2D: Stage 3 - Bitemporal Observation Ledgers & Holdings

CREATE TABLE nav_observation (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    effective_date DATE NOT NULL,
    nav_value NUMERIC(20, 8) NOT NULL,
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
    CONSTRAINT uq_nav_observation UNIQUE (scheme_option_id, effective_date, revision_seq)
);

CREATE TABLE benchmark_observation (
    id BIGSERIAL PRIMARY KEY,
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    effective_date DATE NOT NULL,
    index_level NUMERIC(20, 8) NOT NULL,
    revision_seq INTEGER NOT NULL DEFAULT 1,
    is_latest_revision BOOLEAN NOT NULL DEFAULT TRUE,
    availability_time TIMESTAMPTZ NOT NULL,
    ingestion_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    CONSTRAINT uq_bm_observation UNIQUE (benchmark_id, effective_date, revision_seq)
);

CREATE TABLE portfolio_snapshot (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    portfolio_date DATE NOT NULL,
    revision_seq INTEGER NOT NULL DEFAULT 1,
    is_latest_revision BOOLEAN NOT NULL DEFAULT TRUE,
    availability_time TIMESTAMPTZ NOT NULL,
    reported_total_net_assets NUMERIC(24, 4),
    reported_holdings_count INTEGER,
    sum_reported_weights NUMERIC(10, 6),
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    CONSTRAINT uq_portfolio_snapshot UNIQUE (scheme_option_id, portfolio_date, revision_seq)
);

CREATE TABLE portfolio_holding (
    id BIGSERIAL PRIMARY KEY,
    portfolio_snapshot_id BIGINT NOT NULL REFERENCES portfolio_snapshot(id),
    security_id BIGINT NOT NULL REFERENCES security(id),
    reported_weight NUMERIC(10, 6) NOT NULL,
    market_value NUMERIC(24, 4),
    quantity NUMERIC(24, 4),
    holding_rank INTEGER
);

-- Point-in-Time indexes
CREATE INDEX idx_nav_obs_pit ON nav_observation (
    scheme_option_id, effective_date, availability_time, revision_seq DESC
);
CREATE INDEX idx_nav_obs_latest ON nav_observation (
    scheme_option_id, effective_date
) WHERE is_latest_revision = TRUE;

CREATE INDEX idx_bm_obs_pit ON benchmark_observation (
    benchmark_id, effective_date, availability_time, revision_seq DESC
);
CREATE INDEX idx_bm_obs_latest ON benchmark_observation (
    benchmark_id, effective_date
) WHERE is_latest_revision = TRUE;

CREATE INDEX idx_portfolio_snap_pit ON portfolio_snapshot (
    scheme_option_id, portfolio_date, availability_time, revision_seq DESC
);
CREATE INDEX idx_holdings_snapshot ON portfolio_holding (
    portfolio_snapshot_id, security_id
);
