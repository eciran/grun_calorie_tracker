ALTER TABLE step_goals
    ADD COLUMN IF NOT EXISTS reminder_interval_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS reminder_start_time TIME NOT NULL DEFAULT '09:00',
    ADD COLUMN IF NOT EXISTS reminder_end_time TIME NOT NULL DEFAULT '21:00';

ALTER TABLE step_goals
    DROP CONSTRAINT IF EXISTS chk_step_goals_reminder_interval,
    DROP CONSTRAINT IF EXISTS chk_step_goals_reminder_window;

ALTER TABLE step_goals
    ADD CONSTRAINT chk_step_goals_reminder_interval
        CHECK (reminder_interval_minutes BETWEEN 30 AND 180),
    ADD CONSTRAINT chk_step_goals_reminder_window
        CHECK (reminder_start_time < reminder_end_time);