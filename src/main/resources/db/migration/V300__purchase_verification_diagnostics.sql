ALTER TABLE subscription_verifications
    ADD COLUMN created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reason_code VARCHAR(80),
    ADD COLUMN resolved_product_id VARCHAR(255),
    ADD COLUMN provider_transaction_id VARCHAR(255);

UPDATE subscription_verifications SET created_at = updated_at;
CREATE INDEX idx_subscription_verifications_status_updated
    ON subscription_verifications (status, updated_at);
