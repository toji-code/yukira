-- V002__create_time_series_data.sql
-- YUKIRA MVP
-- Data sources and core financial time-series observations


CREATE TABLE data_source (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(255),
    source_type VARCHAR(100) NOT NULL,
    data_domain VARCHAR(100),
    reference_url TEXT,
    collection_method VARCHAR(100),
    reliability_class VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_data_source_name_provider
        UNIQUE (name, provider)
);


CREATE TABLE nav_observation (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL,
    observation_date DATE NOT NULL,
    nav_value NUMERIC(20,10) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    source_id BIGINT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_nav_scheme_option
        FOREIGN KEY (scheme_option_id)
        REFERENCES scheme_option(id),

    CONSTRAINT fk_nav_source
        FOREIGN KEY (source_id)
        REFERENCES data_source(id),

    CONSTRAINT chk_nav_value
        CHECK (nav_value >= 0),

    CONSTRAINT uq_nav_observation
        UNIQUE (scheme_option_id, observation_date, source_id)
);


CREATE TABLE benchmark_observation (
    id BIGSERIAL PRIMARY KEY,
    benchmark_id BIGINT NOT NULL,
    observation_date DATE NOT NULL,
    index_value NUMERIC(20,10) NOT NULL,
    currency CHAR(3),
    source_id BIGINT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_benchmark_observation_benchmark
        FOREIGN KEY (benchmark_id)
        REFERENCES benchmark(id),

    CONSTRAINT fk_benchmark_observation_source
        FOREIGN KEY (source_id)
        REFERENCES data_source(id),

    CONSTRAINT chk_benchmark_index_value
        CHECK (index_value >= 0),

    CONSTRAINT uq_benchmark_observation
        UNIQUE (benchmark_id, observation_date, source_id)
);


CREATE TABLE security_price_observation (
    id BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL,
    observation_date DATE NOT NULL,
    price NUMERIC(20,10) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    source_id BIGINT NOT NULL,
    retrieved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'UNVALIDATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_security_price_security
        FOREIGN KEY (security_id)
        REFERENCES security(id),

    CONSTRAINT fk_security_price_source
        FOREIGN KEY (source_id)
        REFERENCES data_source(id),

    CONSTRAINT chk_security_price
        CHECK (price >= 0),

    CONSTRAINT uq_security_price_observation
        UNIQUE (security_id, observation_date, source_id)
);


CREATE INDEX idx_nav_observation_date
    ON nav_observation (scheme_option_id, observation_date);

CREATE INDEX idx_benchmark_observation_date
    ON benchmark_observation (benchmark_id, observation_date);

CREATE INDEX idx_security_price_observation_date
    ON security_price_observation (security_id, observation_date);

CREATE INDEX idx_nav_source
    ON nav_observation (source_id);

CREATE INDEX idx_benchmark_observation_source
    ON benchmark_observation (source_id);

CREATE INDEX idx_security_price_source
    ON security_price_observation (source_id);