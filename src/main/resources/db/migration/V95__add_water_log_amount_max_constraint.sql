DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_water_logs_amount_ml_range'
    ) THEN
        ALTER TABLE water_logs
            ADD CONSTRAINT chk_water_logs_amount_ml_range CHECK (amount_ml BETWEEN 1 AND 5000) NOT VALID;
    END IF;
END $$;