ALTER TABLE ai_request_history
    DROP CONSTRAINT IF EXISTS chk_ai_request_history_provider;

ALTER TABLE ai_request_history
    ADD CONSTRAINT chk_ai_request_history_provider CHECK (provider IN (
        'DISABLED',
        'LOG',
        'HTTP_JSON',
        'OPENAI'
    ));