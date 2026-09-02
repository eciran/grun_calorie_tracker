ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS ai_plan_used_this_period INTEGER,
    ADD COLUMN IF NOT EXISTS ai_credit_allocation_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_provider_event_at TIMESTAMP WITH TIME ZONE;

UPDATE subscriptions
SET ai_plan_used_this_period = GREATEST(
        0,
        COALESCE(ai_used_this_period, 0) - COALESCE(ai_addon_used, 0)
    )
WHERE ai_plan_used_this_period IS NULL;

ALTER TABLE subscriptions
    ALTER COLUMN ai_plan_used_this_period SET DEFAULT 0,
    ALTER COLUMN ai_plan_used_this_period SET NOT NULL;

ALTER TABLE subscription_provider_events
    ADD COLUMN IF NOT EXISTS new_product_id VARCHAR(255);

CREATE TABLE IF NOT EXISTS subscription_credit_allocations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    allocation_key VARCHAR(64) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    quota_amount INTEGER NOT NULL,
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    transaction_id VARCHAR(255),
    original_transaction_id VARCHAR(255),
    first_provider_event_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_subscription_credit_allocation UNIQUE (provider, user_id, allocation_key),
    CONSTRAINT chk_subscription_credit_allocation_quota CHECK (quota_amount BETWEEN 0 AND 10000)
);

CREATE INDEX IF NOT EXISTS idx_subscription_credit_allocations_user_created
    ON subscription_credit_allocations(user_id, created_at DESC);
