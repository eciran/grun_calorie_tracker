ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100) NOT NULL DEFAULT 'legacy';

ALTER TABLE ai_request_history
    ALTER COLUMN prompt_version DROP DEFAULT;
