ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS login_locked_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_failed_login_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_users_login_locked_until
    ON users (login_locked_until);
