ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS snapshot_calories DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_protein DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_carbs DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_fat DOUBLE PRECISION;

UPDATE food_logs f
SET snapshot_calories = COALESCE(fi.calories, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0,
    snapshot_protein = COALESCE(fi.protein, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0,
    snapshot_carbs = COALESCE(fi.carbs, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0,
    snapshot_fat = COALESCE(fi.fat, 0) * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0
FROM food_items fi
WHERE f.food_id = fi.id
  AND (f.snapshot_calories IS NULL
       OR f.snapshot_protein IS NULL
       OR f.snapshot_carbs IS NULL
       OR f.snapshot_fat IS NULL);
