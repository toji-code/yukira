-- V21: Analytical Quality Score V1 & Indian Mutual Fund Universe Expansion Foundation
--
-- 1. Universe Model Adjustments:
--    - Make inception_date in scheme nullable (AMFI daily feeds omit inception dates; zero fabricated dates)
--    - Add category and subcategory to scheme for discovery and peer grouping
--    - Broaden scheme_option option_type check constraint for broader AMFI universe (IDCW, BONUS, OTHER)

ALTER TABLE scheme ALTER COLUMN inception_date DROP NOT NULL;
ALTER TABLE scheme ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE scheme ADD COLUMN IF NOT EXISTS subcategory VARCHAR(100);

ALTER TABLE scheme_option DROP CONSTRAINT IF EXISTS chk_option_type;
ALTER TABLE scheme_option ADD CONSTRAINT chk_option_type 
    CHECK (option_type IN ('GROWTH', 'IDCW_PAYOUT', 'IDCW_REINVESTMENT', 'IDCW', 'BONUS', 'OTHER'));

-- 2. Analytical Score Infrastructure (0–100 numerical score, separate 0–100 confidence)
CREATE TABLE IF NOT EXISTS analytical_score (
    id BIGSERIAL PRIMARY KEY,
    scheme_option_id BIGINT NOT NULL REFERENCES scheme_option(id),
    calculation_run_id BIGINT REFERENCES calculation_run(id),
    score NUMERIC(5, 2),
    confidence NUMERIC(5, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    score_version VARCHAR(50) NOT NULL DEFAULT 'YUKIRA_SCORE_V1',
    methodology_status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    as_of_date DATE NOT NULL,
    knowledge_cutoff_time TIMESTAMPTZ NOT NULL,
    reference_population VARCHAR(100) NOT NULL,
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_analytical_score_range CHECK (score IS NULL OR (score >= 0.00 AND score <= 100.00)),
    CONSTRAINT chk_analytical_confidence_range CHECK (confidence >= 0.00 AND confidence <= 100.00),
    CONSTRAINT chk_analytical_score_status CHECK (status IN ('AVAILABLE', 'PARTIAL', 'INSUFFICIENT_DATA', 'DATA_QUALITY_LIMITED', 'NOT_APPLICABLE'))
);

-- 3. Score Dimensions (Return Quality, Risk Quality, Benchmark-Relative Quality, Consistency/Downside, Evidence Confidence)
CREATE TABLE IF NOT EXISTS score_dimension (
    id BIGSERIAL PRIMARY KEY,
    analytical_score_id BIGINT NOT NULL REFERENCES analytical_score(id) ON DELETE CASCADE,
    dimension VARCHAR(50) NOT NULL,
    dimension_name VARCHAR(100) NOT NULL,
    score NUMERIC(5, 2),
    weight NUMERIC(5, 4) NOT NULL,
    status VARCHAR(30) NOT NULL,
    confidence NUMERIC(5, 2) NOT NULL,
    eligible_metric_count INT NOT NULL DEFAULT 0,
    total_metric_count INT NOT NULL DEFAULT 0,
    diagnostics JSONB,
    CONSTRAINT chk_dimension_score_range CHECK (score IS NULL OR (score >= 0.00 AND score <= 100.00))
);

-- 4. Score Metric Contribution (Decomposable auditable metric contributions)
CREATE TABLE IF NOT EXISTS score_metric_contribution (
    id BIGSERIAL PRIMARY KEY,
    score_dimension_id BIGINT NOT NULL REFERENCES score_dimension(id) ON DELETE CASCADE,
    metric_code VARCHAR(50) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    raw_value NUMERIC(30, 10),
    normalized_value NUMERIC(10, 4),
    direction VARCHAR(30) NOT NULL,
    weight NUMERIC(5, 4) NOT NULL,
    contribution NUMERIC(10, 4),
    eligibility VARCHAR(30) NOT NULL,
    exclusion_reason TEXT,
    metric_result_id BIGINT REFERENCES metric_result(id),
    unit VARCHAR(30)
);

-- 5. Indexes for fast lookup and discovery
CREATE INDEX IF NOT EXISTS idx_analytical_score_lookup ON analytical_score (scheme_option_id, as_of_date, score_version);
CREATE INDEX IF NOT EXISTS idx_analytical_score_run ON analytical_score (calculation_run_id);
CREATE INDEX IF NOT EXISTS idx_score_dim_score_id ON score_dimension (analytical_score_id);
CREATE INDEX IF NOT EXISTS idx_score_metric_dim_id ON score_metric_contribution (score_dimension_id);
CREATE INDEX IF NOT EXISTS idx_scheme_cat_subcat ON scheme (category, subcategory);
CREATE INDEX IF NOT EXISTS idx_scheme_amc_id ON scheme (amc_id);
