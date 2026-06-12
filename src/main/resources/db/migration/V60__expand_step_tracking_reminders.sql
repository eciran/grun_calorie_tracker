ALTER TABLE users
    ADD COLUMN IF NOT EXISTS step_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE step_goals
    ADD COLUMN IF NOT EXISTS reminder_time TIME NOT NULL DEFAULT '20:00',
    ADD COLUMN IF NOT EXISTS reminder_threshold_percent INTEGER NOT NULL DEFAULT 70,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP;

ALTER TABLE step_goals
    DROP CONSTRAINT IF EXISTS chk_step_goals_reminder_threshold;

ALTER TABLE step_goals
    ADD CONSTRAINT chk_step_goals_reminder_threshold
        CHECK (reminder_threshold_percent BETWEEN 1 AND 99);
