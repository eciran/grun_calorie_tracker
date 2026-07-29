CREATE TABLE admin_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    session_token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_admin_sessions_active_user ON admin_sessions(user_id, last_activity_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX idx_admin_sessions_expiry ON admin_sessions(absolute_expires_at);
