ALTER TABLE subscription_verifications
    ADD COLUMN recovery_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN expected_transaction_id VARCHAR(255);

UPDATE subscription_verifications SET recovery_started_at = created_at;
