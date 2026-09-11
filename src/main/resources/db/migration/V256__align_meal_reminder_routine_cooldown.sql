ALTER TABLE meal_reminder_policies
    DROP CONSTRAINT IF EXISTS meal_reminder_policies_routine_gap_minutes_check;

ALTER TABLE meal_reminder_policies
    ADD CONSTRAINT meal_reminder_policies_routine_gap_minutes_check
    CHECK (routine_gap_minutes >= 30);
