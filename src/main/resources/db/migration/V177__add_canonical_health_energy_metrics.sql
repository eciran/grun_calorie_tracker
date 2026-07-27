ALTER TABLE device_data
    ADD COLUMN IF NOT EXISTS active_energy_calories DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS resting_energy_calories DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS total_energy_calories DOUBLE PRECISION;

UPDATE device_data
SET active_energy_calories = calories_burned
WHERE active_energy_calories IS NULL
  AND calories_burned IS NOT NULL;

ALTER TABLE device_data
    DROP CONSTRAINT IF EXISTS chk_device_data_energy_non_negative;

ALTER TABLE device_data
    ADD CONSTRAINT chk_device_data_energy_non_negative CHECK (
        (active_energy_calories IS NULL OR active_energy_calories >= 0)
        AND (resting_energy_calories IS NULL OR resting_energy_calories >= 0)
        AND (total_energy_calories IS NULL OR total_energy_calories >= 0)
    );

CREATE INDEX IF NOT EXISTS idx_device_data_user_recorded_at
    ON device_data (user_id, recorded_at DESC);