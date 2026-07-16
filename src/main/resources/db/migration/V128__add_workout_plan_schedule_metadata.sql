ALTER TABLE workout_plans
    ADD COLUMN IF NOT EXISTS schedule_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS schedule_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_workout_plans_user_schedule
    ON workout_plans(user_id, active, schedule_updated_at DESC);