ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS catalog_type VARCHAR(255);

ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS source_key VARCHAR(255);

UPDATE food_items
SET catalog_type = CASE
    WHEN is_custom = true THEN 'USER_CUSTOM'
    ELSE 'BRANDED_PRODUCT'
END
WHERE catalog_type IS NULL;

UPDATE food_items
SET source_key = concat('barcode:', normalized_barcode)
WHERE source_key IS NULL
  AND normalized_barcode IS NOT NULL
  AND trim(normalized_barcode) <> '';

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_catalog_type
        CHECK (catalog_type IS NULL OR catalog_type IN ('BRANDED_PRODUCT', 'GENERIC_INGREDIENT', 'LOCAL_DISH', 'USER_CUSTOM'));

CREATE INDEX IF NOT EXISTS idx_food_items_catalog_type
    ON food_items (catalog_type);

CREATE INDEX IF NOT EXISTS idx_food_items_source_key
    ON food_items (source_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_food_items_source_key
    ON food_items (source_key)
    WHERE source_key IS NOT NULL;
