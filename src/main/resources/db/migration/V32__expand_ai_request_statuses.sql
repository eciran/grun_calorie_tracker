ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_status;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_status CHECK (status IN (
        'DRAFT_CREATED',
        'CONFIRMED',
        'REJECTED',
        'FAILED'
    ));
