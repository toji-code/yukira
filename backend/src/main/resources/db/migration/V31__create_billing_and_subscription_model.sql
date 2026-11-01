-- Phase C2: Subscription, Payment, and Entitlement Data Model

CREATE TABLE subscription_plan (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    price_inr NUMERIC(10, 2) NOT NULL,
    billing_interval VARCHAR(16) NOT NULL, -- 'FREE', 'MONTHLY', 'ANNUAL'
    max_portfolios INT NOT NULL DEFAULT 1,
    max_holdings_per_portfolio INT NOT NULL DEFAULT 20,
    max_saved_funds INT NOT NULL DEFAULT 25,
    can_import_cas BOOLEAN NOT NULL DEFAULT false,
    can_access_api BOOLEAN NOT NULL DEFAULT false,
    api_rate_limit_per_min INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE investor_subscription (
    id BIGSERIAL PRIMARY KEY,
    investor_id BIGINT NOT NULL REFERENCES investor(id),
    plan_id VARCHAR(32) NOT NULL REFERENCES subscription_plan(id),
    provider VARCHAR(32) NOT NULL DEFAULT 'RAZORPAY',
    provider_customer_id VARCHAR(64),
    provider_subscription_id VARCHAR(64),
    provider_order_id VARCHAR(64),
    provider_payment_id VARCHAR(64),
    status VARCHAR(32) NOT NULL, -- 'NONE', 'CHECKOUT_INITIATED', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED', 'PAYMENT_FAILED'
    payment_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID', -- 'UNPAID', 'PENDING', 'PAID', 'FAILED', 'REFUNDED'
    entitlement_status VARCHAR(32) NOT NULL DEFAULT 'STANDARD', -- 'STANDARD', 'ELEVATED_PREVIEW', 'ACTIVE_PRO', 'EXPIRED'
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_investor_subscription UNIQUE (investor_id)
);

CREATE TABLE subscription_webhook_event (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL DEFAULT 'RAZORPAY',
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT false,
    processed BOOLEAN NOT NULL DEFAULT false,
    processing_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Commercial Plans
INSERT INTO subscription_plan (
    id, name, description, price_inr, billing_interval,
    max_portfolios, max_holdings_per_portfolio, max_saved_funds,
    can_import_cas, can_access_api, api_rate_limit_per_min, is_active
) VALUES
(
    'FREE',
    'Community Explorer',
    'Full access to mutual fund discovery, deterministic metric calculation, point-in-time Evidence Explorer, Comparative Screener, and standard Research Workspace.',
    0.00,
    'FREE',
    1,
    20,
    25,
    false,
    false,
    0,
    true
),
(
    'PRO_MONTHLY',
    'Institutional Research Pro (Monthly)',
    'Advanced research capabilities for allocators: unlimited workspace funds, multi-portfolio tracking, statement imports, elevated API access, and exportable audit packages.',
    1999.00,
    'MONTHLY',
    5,
    100,
    1000,
    true,
    true,
    60,
    true
),
(
    'PRO_ANNUAL',
    'Institutional Research Pro (Annual)',
    'All Institutional Research Pro capabilities with annual commitment discount (2 months complimentary preview).',
    19990.00,
    'ANNUAL',
    10,
    250,
    5000,
    true,
    true,
    120,
    true
)
ON CONFLICT (id) DO NOTHING;
