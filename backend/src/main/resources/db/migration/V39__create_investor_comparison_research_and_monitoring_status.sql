-- Flyway Migration V39: Create Investor Comparison Research Model & Monitoring Lifecycle Status
-- Schema authority for saving multi-fund comparison research to investor workspace and tracking condition lifecycle.

CREATE TABLE IF NOT EXISTS investor_comparison_research (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    comparison_title VARCHAR(255) NOT NULL,
    scheme_option_ids_csv VARCHAR(255) NOT NULL,
    as_of_date DATE NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    benchmark_code VARCHAR(64),
    compatibility_status VARCHAR(64) NOT NULL,
    metrics_summary_json TEXT,
    investor_thesis TEXT,
    investor_notes TEXT,
    associated_finding_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_investor_comparison_research_investor 
ON investor_comparison_research(investor_id, created_at DESC);

-- Extend investor_monitoring_condition with explicit lifecycle status and originating finding reference
ALTER TABLE investor_monitoring_condition 
ADD COLUMN IF NOT EXISTS condition_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE investor_monitoring_condition 
ADD COLUMN IF NOT EXISTS originating_finding_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_monitoring_cond_status 
ON investor_monitoring_condition(investor_id, condition_status);
