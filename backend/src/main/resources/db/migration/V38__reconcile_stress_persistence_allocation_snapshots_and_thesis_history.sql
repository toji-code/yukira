-- Flyway Migration V38: Reconcile Stress Persistence, Allocation Snapshots, Signal Lineage, and Research Thesis History (Tasks 100-129)
-- Authority for immutable portfolio allocation snapshots, finding transition audit, source-revision signal lineage, and durable thesis history.

-- 1. Stress Finding Lifecycle Transition and Investor Notes (Task 101, 102)
ALTER TABLE investor_stress_finding ADD COLUMN IF NOT EXISTS transition_reason TEXT;
ALTER TABLE investor_stress_finding ADD COLUMN IF NOT EXISTS transition_by VARCHAR(50) DEFAULT 'SYSTEM';
ALTER TABLE investor_stress_finding ADD COLUMN IF NOT EXISTS investor_notes TEXT;

CREATE TABLE IF NOT EXISTS investor_stress_finding_transition (
    id BIGSERIAL PRIMARY KEY,
    finding_record_id BIGINT NOT NULL REFERENCES investor_stress_finding(id) ON DELETE CASCADE,
    finding_id VARCHAR(100) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    transition_by VARCHAR(50) NOT NULL, -- 'SYSTEM' or 'INVESTOR'
    transition_reason TEXT,
    investor_notes TEXT,
    calculation_run_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stress_finding_trans_record ON investor_stress_finding_transition(finding_record_id);
CREATE INDEX IF NOT EXISTS idx_stress_finding_trans_finding_id ON investor_stress_finding_transition(finding_id);

-- 2. Stress Data Quality Signal Source-Revision Lineage (Task 103)
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS first_detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS latest_detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS observation_interval_start DATE;
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS observation_interval_end DATE;
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS benchmark_code VARCHAR(50);
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS superseded_by_revision_seq INT;
ALTER TABLE investor_stress_data_quality_signal ADD COLUMN IF NOT EXISTS investor_action_notes TEXT;

CREATE INDEX IF NOT EXISTS idx_stress_dq_signal_code ON investor_stress_data_quality_signal(signal_code);
CREATE INDEX IF NOT EXISTS idx_stress_dq_signal_constituent ON investor_stress_data_quality_signal(constituent_code);

-- 3. Immutable Portfolio Allocation Snapshot Persistence (Task 105, 106)
CREATE TABLE IF NOT EXISTS investor_portfolio_allocation_snapshot (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(100) NOT NULL UNIQUE,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    portfolio_id BIGINT,
    effective_date DATE NOT NULL,
    valuation_as_of_date DATE NOT NULL,
    knowledge_cutoff TIMESTAMP WITH TIME ZONE NOT NULL,
    known_valued_capital NUMERIC(24, 4),
    total_portfolio_capital NUMERIC(24, 4),
    coverage_pct NUMERIC(10, 4),
    valued_position_count INT NOT NULL,
    unvalued_position_count INT NOT NULL,
    total_position_count INT NOT NULL,
    weight_calculation_convention VARCHAR(100) NOT NULL,
    weight_normalization_denominator NUMERIC(10, 4) NOT NULL,
    snapshot_fingerprint VARCHAR(64) NOT NULL,
    is_immutable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_port_alloc_snapshot_investor ON investor_portfolio_allocation_snapshot(investor_id);
CREATE INDEX IF NOT EXISTS idx_port_alloc_snapshot_portfolio ON investor_portfolio_allocation_snapshot(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_port_alloc_snapshot_fingerprint ON investor_portfolio_allocation_snapshot(snapshot_fingerprint);

CREATE TABLE IF NOT EXISTS investor_portfolio_allocation_snapshot_item (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES investor_portfolio_allocation_snapshot(id) ON DELETE CASCADE,
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    scheme_code VARCHAR(100),
    scheme_name VARCHAR(255),
    units NUMERIC(19, 4),
    nav_price NUMERIC(19, 4),
    market_value NUMERIC(24, 4),
    allocation_weight NUMERIC(10, 6),
    is_valued BOOLEAN NOT NULL DEFAULT TRUE,
    valuation_status VARCHAR(50) NOT NULL DEFAULT 'VALUED',
    valuation_source_ref VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_port_alloc_item_snapshot ON investor_portfolio_allocation_snapshot_item(snapshot_id);
CREATE INDEX IF NOT EXISTS idx_port_alloc_item_scheme_opt ON investor_portfolio_allocation_snapshot_item(scheme_option_id);

-- 4. Durable Investor Research Thesis Version History (Task 120, 121)
CREATE TABLE IF NOT EXISTS investor_research_thesis_history (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL REFERENCES investor_watchlist(id) ON DELETE CASCADE,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    scheme_id BIGINT NOT NULL REFERENCES scheme(id),
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    thesis_version INT NOT NULL DEFAULT 1,
    thesis_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE_HYPOTHESIS',
    reason_saved TEXT,
    investigation_question TEXT,
    next_step TEXT,
    associated_finding_id VARCHAR(100),
    evidence_context TEXT,
    reconsideration_conditions TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_thesis_history_watchlist ON investor_research_thesis_history(watchlist_id);
CREATE INDEX IF NOT EXISTS idx_thesis_history_investor ON investor_research_thesis_history(investor_id);
CREATE INDEX IF NOT EXISTS idx_thesis_history_finding ON investor_research_thesis_history(associated_finding_id);

