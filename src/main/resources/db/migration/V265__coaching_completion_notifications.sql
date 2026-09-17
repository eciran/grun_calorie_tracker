-- Existing confirmed results must not generate a historical notification burst.
ALTER TABLE ai_request_history
    ADD COLUMN coaching_completion_notification_eligible BOOLEAN NOT NULL DEFAULT FALSE;
