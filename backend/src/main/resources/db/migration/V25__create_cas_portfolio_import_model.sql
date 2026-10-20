-- V25: CAS / External Mutual Fund Portfolio Import Staging & Provenance Model

CREATE TABLE portfolio_import (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    source_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL DEFAULT 'CAS_STATEMENT',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    total_records_detected INT NOT NULL DEFAULT 0,
    resolved_records_count INT NOT NULL DEFAULT 0,
    unresolved_records_count INT NOT NULL DEFAULT 0,
    ambiguous_records_count INT NOT NULL DEFAULT 0,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    statement_date DATE,
    parser_version VARCHAR(64) NOT NULL DEFAULT 'CAS_PARSER_V1',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_portfolio_import_investor ON portfolio_import(investor_id);

CREATE TABLE portfolio_import_holding (
    id BIGSERIAL PRIMARY KEY,
    portfolio_import_id BIGINT NOT NULL REFERENCES portfolio_import(id) ON DELETE CASCADE,
    raw_scheme_name VARCHAR(255) NOT NULL,
    folio_number VARCHAR(64),
    isin VARCHAR(32),
    amfi_code VARCHAR(32),
    units NUMERIC(19, 4) NOT NULL,
    cost_basis_amount NUMERIC(19, 4),
    market_value NUMERIC(19, 4),
    nav NUMERIC(19, 4),
    resolved_scheme_option_id BIGINT REFERENCES scheme_option(id),
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    resolution_method VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_portfolio_import_holding_import ON portfolio_import_holding(portfolio_import_id);
