ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_created_at
    ON users (created_at)
    WHERE created_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_last_active_at
    ON users (last_active_at)
    WHERE last_active_at IS NOT NULL;

CREATE TABLE user_daily_activities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_date DATE NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_source VARCHAR(32) NOT NULL,
    last_source VARCHAR(32) NOT NULL,
    CONSTRAINT uq_user_daily_activity UNIQUE (user_id, activity_date),
    CONSTRAINT chk_user_daily_activity_source CHECK (
        first_source IN ('PASSWORD_LOGIN', 'FEDERATED_LOGIN', 'TOKEN_REFRESH', 'APP_STARTUP')
        AND last_source IN ('PASSWORD_LOGIN', 'FEDERATED_LOGIN', 'TOKEN_REFRESH', 'APP_STARTUP')
    ),
    CONSTRAINT chk_user_daily_activity_seen_order CHECK (last_seen_at >= first_seen_at)
);

CREATE INDEX idx_user_daily_activities_date_user
    ON user_daily_activities (activity_date, user_id);

CREATE INDEX idx_user_daily_activities_last_seen
    ON user_daily_activities (last_seen_at);
