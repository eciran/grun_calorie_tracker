CREATE TABLE IF NOT EXISTS water_reminder_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    interval_minutes INTEGER NOT NULL DEFAULT 120,
    start_time TIME NOT NULL DEFAULT '09:00:00',
    end_time TIME NOT NULL DEFAULT '21:00:00',
    last_reminder_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_water_reminder_interval_range CHECK (interval_minutes BETWEEN 30 AND 240),
    CONSTRAINT chk_water_reminder_time_window CHECK (start_time < end_time)
);

CREATE INDEX IF NOT EXISTS idx_water_reminder_settings_enabled ON water_reminder_settings(enabled);
