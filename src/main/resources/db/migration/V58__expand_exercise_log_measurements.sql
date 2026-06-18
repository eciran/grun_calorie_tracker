ALTER TABLE exercise_logs
    ADD COLUMN IF NOT EXISTS measurement_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS set_count INTEGER,
    ADD COLUMN IF NOT EXISTS reps INTEGER,
    ADD COLUMN IF NOT EXISTS weight_kg DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS distance_km DOUBLE PRECISION;

UPDATE exercise_logs
SET measurement_type = 'DURATION'
WHERE measurement_type IS NULL
  AND duration_minutes IS NOT NULL;

ALTER TABLE exercise_logs
    ADD CONSTRAINT chk_exercise_logs_measurement_values
        CHECK (
            duration_minutes IS NOT NULL
            OR set_count IS NOT NULL
            OR reps IS NOT NULL
            OR weight_kg IS NOT NULL
            OR distance_km IS NOT NULL
        );
