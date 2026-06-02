ALTER TABLE ai_request_history
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40),
    ADD COLUMN IF NOT EXISTS rejection_feedback TEXT;
