ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS note VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS primary_action VARCHAR(64),
    ADD COLUMN IF NOT EXISTS action_amount_ml INTEGER;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS chk_notifications_action_amount_ml;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_action_amount_ml
        CHECK (action_amount_ml IS NULL OR action_amount_ml BETWEEN 1 AND 6000);

CREATE INDEX IF NOT EXISTS idx_notifications_primary_action
    ON notifications (primary_action)
    WHERE primary_action IS NOT NULL;
