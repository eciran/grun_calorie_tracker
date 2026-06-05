CREATE TABLE fasting_plans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    plan_type VARCHAR(30) NOT NULL,
    fasting_hours INTEGER NOT NULL,
    eating_window_hours INTEGER NOT NULL,
    preferred_start_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fasting_plans_type CHECK (plan_type IN ('FASTING_16_8', 'FASTING_18_6', 'FASTING_20_4', 'OMAD', 'CUSTOM')),
    CONSTRAINT chk_fasting_plans_hours CHECK (fasting_hours BETWEEN 1 AND 48 AND eating_window_hours BETWEEN 1 AND 23)
);

CREATE TABLE fasting_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id BIGINT REFERENCES fasting_plans(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    fasting_date DATE NOT NULL,
    started_at TIMESTAMP NOT NULL,
    target_end_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    reminder_sent_at TIMESTAMP,
    target_minutes INTEGER NOT NULL,
    actual_minutes INTEGER,
    target_reached BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fasting_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_fasting_sessions_target_minutes CHECK (target_minutes BETWEEN 30 AND 2880),
    CONSTRAINT chk_fasting_sessions_actual_minutes CHECK (actual_minutes IS NULL OR actual_minutes >= 0)
);

CREATE INDEX idx_fasting_sessions_user_date
    ON fasting_sessions(user_id, fasting_date);

CREATE INDEX idx_fasting_sessions_user_status
    ON fasting_sessions(user_id, status);

CREATE INDEX idx_fasting_sessions_user_started_at
    ON fasting_sessions(user_id, started_at);
