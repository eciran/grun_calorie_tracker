ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS serving_size_grams DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS serving_unit VARCHAR(50);

ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS portion_unit VARCHAR(50),
    ADD COLUMN IF NOT EXISTS normalized_portion_grams DOUBLE PRECISION;

UPDATE food_logs
SET portion_unit = 'GRAM'
WHERE portion_unit IS NULL;

UPDATE food_logs
SET normalized_portion_grams = portion_size
WHERE normalized_portion_grams IS NULL
  AND portion_size IS NOT NULL;
