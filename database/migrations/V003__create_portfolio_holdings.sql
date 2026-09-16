-- V003__create_portfolio_holdings.sql
-- YUKIRA MVP
-- Historical portfolio snapshots and holdings


CREATE TABLE portfolio_snapshot (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL,
    observation_date DATE NOT NULL,
    source_id BIGINT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_portfolio_snapshot_scheme_option
        FOREIGN KEY (scheme_option_id)
        REFERENCES scheme_option(id),

    CONSTRAINT fk_portfolio_snapshot_source
        FOREIGN KEY (source_id)
        REFERENCES data_source(id),

    CONSTRAINT uq_portfolio_snapshot
        UNIQUE (scheme_option_id, observation_date, source_id)
);


CREATE TABLE portfolio_holding (
    id BIGSERIAL PRIMARY KEY,
    portfolio_snapshot_id BIGINT NOT NULL,
    security_id BIGINT NOT NULL,
    quantity NUMERIC(24,10),
    market_value NUMERIC(24,10),
    portfolio_weight NUMERIC(12,8),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_portfolio_holding_snapshot
        FOREIGN KEY (portfolio_snapshot_id)
        REFERENCES portfolio_snapshot(id),

    CONSTRAINT fk_portfolio_holding_security
        FOREIGN KEY (security_id)
        REFERENCES security(id),

    CONSTRAINT chk_portfolio_holding_quantity
        CHECK (quantity IS NULL OR quantity >= 0),

    CONSTRAINT chk_portfolio_holding_market_value
        CHECK (market_value IS NULL OR market_value >= 0),

    CONSTRAINT chk_portfolio_holding_weight
        CHECK (
            portfolio_weight IS NULL
            OR (
                portfolio_weight >= 0
                AND portfolio_weight <= 1
            )
        ),

    CONSTRAINT uq_portfolio_holding_security
        UNIQUE (portfolio_snapshot_id, security_id)
);


CREATE INDEX idx_portfolio_snapshot_scheme_date
    ON portfolio_snapshot (scheme_option_id, observation_date);

CREATE INDEX idx_portfolio_snapshot_source
    ON portfolio_snapshot (source_id);

CREATE INDEX idx_portfolio_holding_snapshot
    ON portfolio_holding (portfolio_snapshot_id);

CREATE INDEX idx_portfolio_holding_security
    ON portfolio_holding (security_id);