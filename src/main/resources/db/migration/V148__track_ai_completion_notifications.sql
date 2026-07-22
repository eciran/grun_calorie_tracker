ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS completion_notified_at TIMESTAMP;

-- Existing finished requests predate completion notifications and must not fan out on deploy.
UPDATE ai_request_history
SET completion_notified_at = COALESCE(completion_notified_at, CURRENT_TIMESTAMP)
WHERE status <> 'PROCESSING';

CREATE INDEX IF NOT EXISTS idx_ai_request_completion_notification
    ON ai_request_history (status, created_at)
    WHERE completion_notified_at IS NULL;