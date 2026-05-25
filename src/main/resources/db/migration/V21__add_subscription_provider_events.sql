ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_product_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_transaction_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_original_transaction_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_provider_event_id VARCHAR(255);

UPDATE subscriptions
SET provider = CASE
        WHEN provider IS NULL THEN provider
        WHEN UPPER(provider) IN ('MANUAL_ADMIN', 'REVENUECAT', 'APPLE_APP_STORE', 'GOOGLE_PLAY') THEN UPPER(provider)
        WHEN UPPER(provider) = 'APPLE' THEN 'APPLE_APP_STORE'
        WHEN UPPER(provider) = 'GOOGLE' THEN 'GOOGLE_PLAY'
        ELSE 'MANUAL_ADMIN'
    END;

CREATE TABLE IF NOT EXISTS subscription_provider_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    provider_app_user_id VARCHAR(255),
    event_type VARCHAR(100),
    product_id VARCHAR(255),
    entitlement_ids VARCHAR(1000),
    transaction_id VARCHAR(255),
    original_transaction_id VARCHAR(255),
    user_id BIGINT REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    raw_payload TEXT NOT NULL,
    processing_error VARCHAR(1000),
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_subscription_provider_event UNIQUE (provider, provider_event_id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_user
    ON subscription_provider_events(user_id);

CREATE INDEX IF NOT EXISTS idx_subscription_provider_events_status
    ON subscription_provider_events(status);
