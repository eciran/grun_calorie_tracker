ALTER TABLE subscription_provider_events
    ADD COLUMN IF NOT EXISTS apple_refund_consent_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS apple_refund_consent_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS plan_quota_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS plan_used_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS addon_quota_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS addon_used_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS entitlement_delivered_snapshot BOOLEAN,
    ADD COLUMN IF NOT EXISTS entitlement_active_snapshot BOOLEAN;

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_refund_evidence
    ON subscription_provider_events(user_id, provider_event_at DESC)
    WHERE cancel_reason = 'CUSTOMER_SUPPORT' OR event_type = 'REFUND_REVERSED';
