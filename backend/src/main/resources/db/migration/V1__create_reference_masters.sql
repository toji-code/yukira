-- Phase 2D: Stage 1 - Reference Masters & Market Calendar

CREATE TABLE amc (
    id BIGSERIAL PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE scheme (
    id BIGSERIAL PRIMARY KEY,
    amc_id BIGINT NOT NULL REFERENCES amc(id),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    inception_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE scheme_plan (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    plan_type VARCHAR(30) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    CONSTRAINT chk_plan_type CHECK (plan_type IN ('DIRECT', 'REGULAR'))
);

CREATE TABLE scheme_option (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES scheme_plan(id),
    option_type VARCHAR(30) NOT NULL,
    amfi_code VARCHAR(50) UNIQUE,
    isin VARCHAR(20) UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT chk_option_type CHECK (option_type IN ('GROWTH', 'IDCW_PAYOUT', 'IDCW_REINVESTMENT'))
);

CREATE TABLE benchmark (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    return_variant VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR'
);

CREATE TABLE security (
    id BIGSERIAL PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL,
    issuer_name VARCHAR(255),
    sector VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE security_identifier (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security(id),
    id_type VARCHAR(30) NOT NULL,
    id_value VARCHAR(100) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    CONSTRAINT uq_sec_id UNIQUE (id_type, id_value, valid_from)
);

CREATE TABLE market_calendar (
    calendar_date DATE PRIMARY KEY,
    is_trading_day BOOLEAN NOT NULL DEFAULT TRUE,
    exchange VARCHAR(20) NOT NULL DEFAULT 'NSE',
    holiday_name VARCHAR(100)
);

CREATE INDEX idx_scheme_amc ON scheme (amc_id);
CREATE INDEX idx_plan_scheme ON scheme_plan (scheme_id);
CREATE INDEX idx_option_plan ON scheme_option (plan_id);
CREATE INDEX idx_sec_id_lookup ON security_identifier (id_type, id_value);
