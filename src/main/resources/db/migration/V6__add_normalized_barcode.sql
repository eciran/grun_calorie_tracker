ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS normalized_barcode VARCHAR(255);

UPDATE food_items
SET normalized_barcode = upper(regexp_replace(barcode, '[[:space:]-]+', '', 'g'))
WHERE normalized_barcode IS NULL
  AND barcode IS NOT NULL
  AND trim(barcode) <> '';

CREATE INDEX IF NOT EXISTS idx_food_items_normalized_barcode
    ON food_items (normalized_barcode);
