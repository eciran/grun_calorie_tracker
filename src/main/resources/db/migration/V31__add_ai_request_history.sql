CREATE TABLE IF NOT EXISTS ai_request_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    request_type VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    input_payload TEXT,
    output_payload TEXT,
    error_message TEXT,
    quota_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms BIGINT,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost DOUBLE PRECISION,
    cost_currency VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_request_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_request_history_type CHECK (request_type IN ('VOICE_FOOD_LOG', 'PHOTO_MEAL_LOG')),
    CONSTRAINT chk_ai_request_history_provider CHECK (provider IN ('DISABLED', 'LOG', 'HTTP_JSON')),
    CONSTRAINT chk_ai_request_history_status CHECK (status IN ('DRAFT_CREATED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_request_history_user_created
    ON ai_request_history(user_id, created_at DESC);
