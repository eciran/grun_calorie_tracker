CREATE TABLE fasting_program_idempotency (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    program_id BIGINT NOT NULL REFERENCES fasting_programs(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fasting_program_idempotency_user_operation_key
        UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX idx_fasting_program_idempotency_expiry
    ON fasting_program_idempotency(expires_at);