ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS confirmation_payload TEXT,
    ADD COLUMN IF NOT EXISTS correction_summary TEXT,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
