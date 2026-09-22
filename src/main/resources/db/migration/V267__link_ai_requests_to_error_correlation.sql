ALTER TABLE ai_request_history
    ADD COLUMN correlation_id VARCHAR(128);

CREATE INDEX idx_ai_request_history_correlation
    ON ai_request_history (correlation_id)
    WHERE correlation_id IS NOT NULL;

ALTER TABLE owner_error_events
    ALTER COLUMN correlation_id TYPE VARCHAR(128);
