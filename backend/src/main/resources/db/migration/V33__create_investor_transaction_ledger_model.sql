-- V33: Investor Transaction Ledger, Cash Flow History, and Import Provenance Model

CREATE TABLE investor_transaction (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    transaction_type VARCHAR(32) NOT NULL,
    transaction_date DATE NOT NULL,
    settlement_date DATE,
    units NUMERIC(19, 4) NOT NULL,
    nav NUMERIC(19, 4),
    gross_amount NUMERIC(19, 4) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    charges_or_taxes NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    dividend_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    source_attribution VARCHAR(32) NOT NULL DEFAULT 'MANUAL_ENTRY',
    source_document_ref VARCHAR(255),
    original_description TEXT,
    import_batch_id VARCHAR(64),
    reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'UNRECONCILED',
    reversal_reference_id BIGINT REFERENCES investor_transaction(id),
    is_reversed BOOLEAN NOT NULL DEFAULT FALSE,
    dedup_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investor_tx_investor_date ON investor_transaction(investor_id, transaction_date DESC);
CREATE INDEX idx_investor_tx_scheme ON investor_transaction(investor_id, scheme_option_id);
CREATE INDEX idx_investor_tx_dedup ON investor_transaction(investor_id, dedup_hash);

CREATE TABLE portfolio_import_transaction (
    id BIGSERIAL PRIMARY KEY,
    portfolio_import_id BIGINT NOT NULL REFERENCES portfolio_import(id) ON DELETE CASCADE,
    raw_scheme_name VARCHAR(255) NOT NULL,
    folio_number VARCHAR(64),
    isin VARCHAR(32),
    amfi_code VARCHAR(32),
    transaction_date DATE NOT NULL,
    raw_type_description VARCHAR(255) NOT NULL,
    normalized_type VARCHAR(32) NOT NULL,
    units NUMERIC(19, 4),
    nav NUMERIC(19, 4),
    amount NUMERIC(19, 4),
    resolved_scheme_option_id BIGINT REFERENCES scheme_option(id),
    resolution_status VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    resolution_method VARCHAR(32) NOT NULL DEFAULT 'UNRESOLVED',
    resolution_notes TEXT,
    dedup_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_import_tx_import_id ON portfolio_import_transaction(portfolio_import_id);

