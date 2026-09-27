ALTER TABLE subscription_provider_events
    ADD COLUMN processing_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP;

CREATE INDEX idx_subscription_provider_event_retry
    ON subscription_provider_events (next_attempt_at)
    WHERE status = 'FAILED' AND next_attempt_at IS NOT NULL;

-- Historical failures require explicit review before replay; do not grant old purchases blindly.
