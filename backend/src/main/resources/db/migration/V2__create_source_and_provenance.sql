-- Phase 2D: Stage 2 - Data Source & Raw Artifact Provenance

CREATE TABLE data_source (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE source_artifact (
    id BIGSERIAL PRIMARY KEY,
    data_source_id BIGINT NOT NULL REFERENCES data_source(id),
    retrieval_timestamp TIMESTAMPTZ NOT NULL,
    artifact_type VARCHAR(30) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    storage_uri VARCHAR(500),
    payload_blob BYTEA
);

CREATE INDEX idx_source_artifact_hash ON source_artifact (sha256_hash);
CREATE INDEX idx_source_artifact_time ON source_artifact (retrieval_timestamp);
