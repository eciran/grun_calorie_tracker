CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_used
    ON password_reset_tokens (user_id, used_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at
    ON password_reset_tokens (expires_at);
