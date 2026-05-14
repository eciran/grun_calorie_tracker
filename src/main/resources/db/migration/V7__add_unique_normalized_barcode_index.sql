CREATE UNIQUE INDEX IF NOT EXISTS uq_food_items_normalized_barcode
    ON food_items (normalized_barcode)
    WHERE normalized_barcode IS NOT NULL;
