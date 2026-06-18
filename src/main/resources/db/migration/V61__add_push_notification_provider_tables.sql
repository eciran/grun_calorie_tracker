CREATE TABLE IF NOT EXISTS user_push_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    platform VARCHAR(32) NOT NULL,
    device_id VARCHAR(255),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    token_value VARCHAR(4096) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_push_tokens_user_enabled
    ON user_push_tokens(user_id, enabled);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_push_tokens_token_hash
    ON user_push_tokens(token_hash);

CREATE TABLE IF NOT EXISTS push_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT REFERENCES notifications(id) ON DELETE SET NULL,
    push_token_id BIGINT REFERENCES user_push_tokens(id) ON DELETE SET NULL,
    provider VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(512),
    error_message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_push_delivery_logs_notification
    ON push_delivery_logs(notification_id);

CREATE INDEX IF NOT EXISTS idx_push_delivery_logs_token
    ON push_delivery_logs(push_token_id);
