-- Phase 2D: Stage 4 - Historical Associations (Validity Intervals)

CREATE TABLE scheme_benchmark_hist (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    benchmark_id BIGINT NOT NULL REFERENCES benchmark(id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE scheme_category_hist (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    category_name VARCHAR(100) NOT NULL,
    sebi_category VARCHAR(100) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE
);

CREATE TABLE scheme_manager_hist (
    id BIGSERIAL PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    manager_name VARCHAR(255) NOT NULL,
    role VARCHAR(100) NOT NULL DEFAULT 'PRIMARY_EQUITY',
    start_date DATE NOT NULL,
    end_date DATE
);

CREATE INDEX idx_scheme_bm_hist ON scheme_benchmark_hist (scheme_id, effective_from);
CREATE INDEX idx_scheme_cat_hist ON scheme_category_hist (scheme_id, effective_from);
CREATE INDEX idx_scheme_mgr_hist ON scheme_manager_hist (scheme_id, start_date);
