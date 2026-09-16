-- V001__create_core_entities.sql
-- YUKIRA MVP
-- Core reference/master entities

CREATE TABLE fund_house (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_fund_house_name
        UNIQUE (name),

    CONSTRAINT chk_fund_house_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE benchmark (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(255),
    benchmark_type VARCHAR(100),
    asset_class VARCHAR(100),
    currency CHAR(3),
    return_methodology VARCHAR(50),
    effective_from DATE,
    effective_to DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_benchmark_name_provider
        UNIQUE (name, provider),

    CONSTRAINT chk_benchmark_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE scheme (
    id BIGSERIAL PRIMARY KEY,
    fund_house_id BIGINT NOT NULL,
    name VARCHAR(500) NOT NULL,
    scheme_category VARCHAR(150),
    scheme_type VARCHAR(100),
    asset_class VARCHAR(100),
    investment_objective TEXT,
    launch_date DATE,
    closure_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_scheme_fund_house
        FOREIGN KEY (fund_house_id)
        REFERENCES fund_house(id),

    CONSTRAINT chk_scheme_dates
        CHECK (
            closure_date IS NULL
            OR launch_date IS NULL
            OR closure_date >= launch_date
        ),

    CONSTRAINT uq_scheme_fund_house_name
        UNIQUE (fund_house_id, name)
);


CREATE TABLE scheme_benchmark (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL,
    benchmark_id BIGINT NOT NULL,
    benchmark_role VARCHAR(30) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_scheme_benchmark_scheme
        FOREIGN KEY (scheme_id)
        REFERENCES scheme(id),

    CONSTRAINT fk_scheme_benchmark_benchmark
        FOREIGN KEY (benchmark_id)
        REFERENCES benchmark(id),

    CONSTRAINT chk_scheme_benchmark_role
        CHECK (
            benchmark_role IN ('PRIMARY', 'SECONDARY')
        ),

    CONSTRAINT chk_scheme_benchmark_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE plan (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    plan_type VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_plan_scheme
        FOREIGN KEY (scheme_id)
        REFERENCES scheme(id),

    CONSTRAINT uq_plan_scheme_name
        UNIQUE (scheme_id, name),

    CONSTRAINT chk_plan_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE scheme_option (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    option_type VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_scheme_option_plan
        FOREIGN KEY (plan_id)
        REFERENCES plan(id),

    CONSTRAINT uq_scheme_option_plan_name
        UNIQUE (plan_id, name),

    CONSTRAINT chk_scheme_option_dates
        CHECK (
            effective_to IS NULL
            OR effective_from IS NULL
            OR effective_to >= effective_from
        )
);


CREATE TABLE security (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    security_type VARCHAR(100) NOT NULL,
    issuer VARCHAR(255),
    isin VARCHAR(20),
    currency CHAR(3),
    country_code CHAR(2),
    sector VARCHAR(150),
    industry VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_security_isin
        UNIQUE (isin)
);