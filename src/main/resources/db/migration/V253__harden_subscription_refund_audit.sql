ALTER TABLE subscription_provider_events
    ADD COLUMN IF NOT EXISTS period_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS environment VARCHAR(40),
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(80),
    ADD COLUMN IF NOT EXISTS expiration_reason VARCHAR(80),
    ADD COLUMN IF NOT EXISTS provider_event_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS purchased_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS expiration_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_transaction
    ON subscription_provider_events(transaction_id);

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_original_transaction
    ON subscription_provider_events(original_transaction_id);

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_refund_reason
    ON subscription_provider_events(cancel_reason, received_at DESC)
    WHERE cancel_reason IS NOT NULL;

ALTER TABLE subscription_credit_allocations
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS revoked_by_event_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_subscription_credit_allocations_revoked
    ON subscription_credit_allocations(user_id, revoked_at DESC)
    WHERE revoked_at IS NOT NULL;
