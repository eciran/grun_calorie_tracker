CREATE TABLE IF NOT EXISTS advanced_fasting_reminder_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pre_start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    start_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    nearing_completion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    completion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    missed_plan_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pre_start_minutes INTEGER NOT NULL DEFAULT 30,
    nearing_completion_minutes INTEGER NOT NULL DEFAULT 15,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_advanced_fasting_pre_start_minutes CHECK (pre_start_minutes BETWEEN 5 AND 180),
    CONSTRAINT chk_advanced_fasting_nearing_minutes CHECK (nearing_completion_minutes BETWEEN 5 AND 120)
);