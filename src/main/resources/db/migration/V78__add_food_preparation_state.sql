ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS preparation_state VARCHAR(40) DEFAULT 'UNSPECIFIED';

UPDATE food_items
SET preparation_state = 'UNSPECIFIED'
WHERE preparation_state IS NULL;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_preparation_state
        CHECK (preparation_state IS NULL OR preparation_state IN (
            'UNSPECIFIED',
            'RAW',
            'COOKED',
            'BOILED',
            'GRILLED',
            'FRIED',
            'BAKED',
            'ROASTED',
            'STEAMED',
            'PREPARED'
        ));

CREATE INDEX IF NOT EXISTS idx_food_items_preparation_state
    ON food_items (preparation_state);
