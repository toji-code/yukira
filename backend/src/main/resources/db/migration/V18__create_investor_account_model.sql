CREATE TABLE investor (
    id BIGSERIAL PRIMARY KEY,
    auth0_subject VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE investor_watchlist (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id),
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_investor_watchlist UNIQUE (investor_id, scheme_id)
);

CREATE TABLE investor_portfolio_holding (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id),
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    units NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_investor_holding UNIQUE (investor_id, scheme_option_id)
);
