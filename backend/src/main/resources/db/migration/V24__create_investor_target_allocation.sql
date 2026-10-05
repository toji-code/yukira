-- V24: Investor Target Allocation persistence table for Portfolio Analytical Rebalancing & Drift Inspection V1

CREATE TABLE investor_target_allocation (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id) ON DELETE CASCADE,
    target_type VARCHAR(32) NOT NULL,
    scheme_option_id BIGINT REFERENCES scheme_option(id),
    category_name VARCHAR(128),
    target_weight_percentage NUMERIC(7,4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_investor_target_alloc_investor ON investor_target_allocation(investor_id);
