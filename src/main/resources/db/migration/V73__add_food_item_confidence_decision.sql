ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS confidence_score INTEGER,
    ADD COLUMN IF NOT EXISTS auto_approved_for_catalog BOOLEAN DEFAULT FALSE;

UPDATE food_items
SET auto_approved_for_catalog = FALSE
WHERE auto_approved_for_catalog IS NULL;
