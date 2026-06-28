ALTER TABLE water_reminder_settings
    ADD COLUMN IF NOT EXISTS daily_target_ml INTEGER NOT NULL DEFAULT 2500;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_water_daily_target_range'
    ) THEN
        ALTER TABLE water_reminder_settings
            ADD CONSTRAINT chk_water_daily_target_range CHECK (daily_target_ml BETWEEN 500 AND 10000);
    END IF;
END $$;
