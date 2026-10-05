-- V22: Fund Information Enrichment
-- Supporting Holdings Provenance, Fund Manager Associations, Expense Ratios (TER), and Scheme Investment Terms

-- 1. Extend scheme_manager_hist with provenance and audit metadata
ALTER TABLE scheme_manager_hist ADD COLUMN IF NOT EXISTS source_artifact_id BIGINT REFERENCES source_artifact(id);
ALTER TABLE scheme_manager_hist ADD COLUMN IF NOT EXISTS as_of_date DATE;
ALTER TABLE scheme_manager_hist ADD COLUMN IF NOT EXISTS quality_assessment VARCHAR(30) DEFAULT 'VALID';

-- 2. Create Scheme Expense Ratio table (Direct vs Regular, Growth vs IDCW separated per scheme option)
CREATE TABLE IF NOT EXISTS scheme_expense_ratio (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    as_of_date DATE NOT NULL,
    expense_ratio NUMERIC(10, 6) NOT NULL,
    plan_type VARCHAR(30) NOT NULL,
    option_type VARCHAR(30) NOT NULL,
    regular_plan_ratio NUMERIC(10, 6),
    availability_time TIMESTAMPTZ NOT NULL,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    quality_assessment VARCHAR(30) NOT NULL DEFAULT 'VALID',
    CONSTRAINT uq_scheme_option_expense UNIQUE (scheme_option_id, as_of_date)
);

CREATE INDEX IF NOT EXISTS idx_scheme_expense_option_date ON scheme_expense_ratio(scheme_option_id, as_of_date);

-- 3. Create Scheme Investment Terms table (Authoritative KIM/SID investment parameters)
CREATE TABLE IF NOT EXISTS scheme_investment_terms (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    min_sip_amount NUMERIC(15, 2),
    sip_frequencies VARCHAR(255),
    min_lumpsum_amount NUMERIC(15, 2),
    min_additional_amount NUMERIC(15, 2),
    lock_in_period_days INTEGER,
    exit_load_description TEXT,
    as_of_date DATE NOT NULL,
    source_artifact_id BIGINT REFERENCES source_artifact(id),
    source_document_title VARCHAR(255),
    quality_assessment VARCHAR(30) NOT NULL DEFAULT 'VALID',
    CONSTRAINT uq_scheme_investment_terms UNIQUE (scheme_id, as_of_date)
);

CREATE INDEX IF NOT EXISTS idx_scheme_inv_terms_scheme_date ON scheme_investment_terms(scheme_id, as_of_date);
