-- Phase C3: Investor Billing History and Invoice Records Model

CREATE TABLE investor_billing_record (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id),
    subscription_id BIGINT REFERENCES investor_subscription(id),
    plan_id VARCHAR(32) NOT NULL REFERENCES subscription_plan(id),
    provider VARCHAR(32) NOT NULL DEFAULT 'RAZORPAY',
    provider_order_id VARCHAR(64),
    provider_payment_id VARCHAR(64),
    provider_invoice_id VARCHAR(64),
    provider_invoice_url TEXT,
    amount_paise BIGINT NOT NULL,
    amount_inr NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    payment_status VARCHAR(32) NOT NULL, -- 'PAID', 'PENDING', 'FAILED', 'REFUNDED'
    billing_reason VARCHAR(64) NOT NULL DEFAULT 'INITIAL_CHECKOUT', -- 'INITIAL_CHECKOUT', 'RENEWAL', 'UPGRADE', 'PLAN_SWITCH'
    failure_reason TEXT,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    provider_event_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_billing_record_investor ON investor_billing_record(investor_id, created_at DESC);
CREATE INDEX idx_billing_record_order ON investor_billing_record(provider_order_id);
CREATE UNIQUE INDEX uq_billing_record_payment ON investor_billing_record(provider_payment_id) WHERE provider_payment_id IS NOT NULL;
