-- Flyway Migration V41: Real Data Coverage, Holdings Instrument Master, FIFO Lots & V1 Product Completion
-- Schema authority for Tasks 210–259 in Project YUKIRA.

-- 1. Official Source Publication Calendar & Reporting-Lag Ledger (Task 215)
CREATE TABLE IF NOT EXISTS source_publication_calendar (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(64) NOT NULL,
    frequency VARCHAR(32) NOT NULL DEFAULT 'DAILY',
    expected_lag_business_days INTEGER NOT NULL DEFAULT 1,
    expected_cutoff_time TIME NOT NULL DEFAULT '23:00:00',
    timezone VARCHAR(32) NOT NULL DEFAULT 'Asia/Kolkata',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_observed_date DATE,
    last_observed_at TIMESTAMP WITH TIME ZONE,
    next_expected_date DATE,
    next_expected_at TIMESTAMP WITH TIME ZONE,
    notes VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_pub_cal_name 
ON source_publication_calendar(source_name, is_active);

-- 2. Scheme Metadata Revision Ledger (Task 211)
CREATE TABLE IF NOT EXISTS scheme_metadata_revision (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT REFERENCES scheme(id) ON DELETE CASCADE,
    scheme_option_id BIGINT REFERENCES scheme_option(id) ON DELETE CASCADE,
    revision_type VARCHAR(64) NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    previous_value TEXT,
    new_value TEXT,
    effective_date DATE NOT NULL,
    availability_time TIMESTAMP WITH TIME ZONE NOT NULL,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    revision_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheme_meta_rev_scheme 
ON scheme_metadata_revision(scheme_id, effective_date DESC);
CREATE INDEX IF NOT EXISTS idx_scheme_meta_rev_option 
ON scheme_metadata_revision(scheme_option_id, effective_date DESC);

-- 3. Multi-Identifier Scheme Alias Table (Task 216)
CREATE TABLE IF NOT EXISTS scheme_identifier_alias (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT REFERENCES scheme_option(id) ON DELETE CASCADE,
    scheme_id BIGINT REFERENCES scheme(id) ON DELETE CASCADE,
    identifier_type VARCHAR(64) NOT NULL,
    identifier_value VARCHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE,
    valid_to DATE,
    source_notes VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scheme_ident_alias_lookup 
ON scheme_identifier_alias(identifier_type, identifier_value);
CREATE INDEX IF NOT EXISTS idx_scheme_ident_alias_option 
ON scheme_identifier_alias(scheme_option_id);

-- 4. Holdings Instrument Master (Task 231)
CREATE TABLE IF NOT EXISTS holdings_instrument_master (
    id BIGSERIAL PRIMARY KEY,
    isin VARCHAR(32) NOT NULL UNIQUE,
    instrument_name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(32) NOT NULL DEFAULT 'EQUITY',
    issuer_name VARCHAR(255),
    industry VARCHAR(128),
    sector_name VARCHAR(128),
    credit_rating VARCHAR(32),
    rating_agency VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_holdings_inst_isin 
ON holdings_instrument_master(isin);
CREATE INDEX IF NOT EXISTS idx_holdings_inst_asset_sector 
ON holdings_instrument_master(asset_class, sector_name);

-- 5. Sector Classification Taxonomy (Task 234)
CREATE TABLE IF NOT EXISTS holdings_sector_classification (
    id BIGSERIAL PRIMARY KEY,
    sector_code VARCHAR(64) NOT NULL UNIQUE,
    sector_name VARCHAR(128) NOT NULL,
    macro_economic_sector VARCHAR(128),
    industry_group VARCHAR(128),
    taxonomy_version VARCHAR(32) NOT NULL DEFAULT 'SEBI_AMFI_2024',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sector_class_code 
ON holdings_sector_classification(sector_code);

-- 6. Market-Cap Categorization Master (Task 235)
CREATE TABLE IF NOT EXISTS holdings_market_cap_classification (
    id BIGSERIAL PRIMARY KEY,
    isin VARCHAR(32) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    market_cap_rank INTEGER,
    market_cap_category VARCHAR(32) NOT NULL,
    effective_period_start DATE NOT NULL,
    effective_period_end DATE NOT NULL,
    source_provenance VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mkt_cap_class_isin_period 
ON holdings_market_cap_classification(isin, effective_period_start, effective_period_end);

-- 7. Extend portfolio_import with CAS lifecycle fields (Tasks 241, 242)
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS source_hash VARCHAR(64);
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS statement_period_start DATE;
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS statement_period_end DATE;
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS investor_pan_masked VARCHAR(32);
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS total_transactions_count INTEGER DEFAULT 0;
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS reconciliation_diagnostics TEXT;
ALTER TABLE portfolio_import ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

-- 8. FIFO Unit Accounting Tax-Lot Ledger (Task 245)
CREATE TABLE IF NOT EXISTS fifo_lot_ledger (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id) ON DELETE CASCADE,
    source_transaction_id BIGINT REFERENCES investor_transaction(id),
    acquisition_date DATE NOT NULL,
    units_acquired NUMERIC(19, 4) NOT NULL,
    units_remaining NUMERIC(19, 4) NOT NULL,
    acquisition_nav NUMERIC(19, 4) NOT NULL,
    total_cost NUMERIC(19, 4) NOT NULL,
    is_depleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fifo_lot_investor_option 
ON fifo_lot_ledger(investor_id, scheme_option_id, acquisition_date ASC);
CREATE INDEX IF NOT EXISTS idx_fifo_lot_depleted 
ON fifo_lot_ledger(investor_id, is_depleted);

-- 9. Extend investor_monitoring_condition with pause lifecycle (Task 251)
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS pause_reason VARCHAR(255);
ALTER TABLE investor_monitoring_condition ADD COLUMN IF NOT EXISTS resume_at TIMESTAMP WITH TIME ZONE;

