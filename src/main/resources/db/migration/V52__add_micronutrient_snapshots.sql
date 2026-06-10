ALTER TABLE food_logs
    ADD COLUMN IF NOT EXISTS snapshot_fiber DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_sugar DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_saturated_fat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_sodium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_potassium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_cholesterol DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_calcium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_iron DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_magnesium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_zinc DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_a DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_c DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_d DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_e DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_b12 DOUBLE PRECISION;

UPDATE food_logs f
SET snapshot_fiber = CASE WHEN fi.fiber IS NULL THEN NULL ELSE fi.fiber * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_sugar = CASE WHEN fi.sugar IS NULL THEN NULL ELSE fi.sugar * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_saturated_fat = CASE WHEN fi.saturated_fat IS NULL THEN NULL ELSE fi.saturated_fat * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_sodium = CASE WHEN fi.sodium IS NULL THEN NULL ELSE fi.sodium * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_potassium = CASE WHEN fi.potassium IS NULL THEN NULL ELSE fi.potassium * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_cholesterol = CASE WHEN fi.cholesterol IS NULL THEN NULL ELSE fi.cholesterol * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_calcium = CASE WHEN fi.calcium IS NULL THEN NULL ELSE fi.calcium * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_iron = CASE WHEN fi.iron IS NULL THEN NULL ELSE fi.iron * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_magnesium = CASE WHEN fi.magnesium IS NULL THEN NULL ELSE fi.magnesium * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_zinc = CASE WHEN fi.zinc IS NULL THEN NULL ELSE fi.zinc * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_vitamin_a = CASE WHEN fi.vitamin_a IS NULL THEN NULL ELSE fi.vitamin_a * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_vitamin_c = CASE WHEN fi.vitamin_c IS NULL THEN NULL ELSE fi.vitamin_c * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_vitamin_d = CASE WHEN fi.vitamin_d IS NULL THEN NULL ELSE fi.vitamin_d * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_vitamin_e = CASE WHEN fi.vitamin_e IS NULL THEN NULL ELSE fi.vitamin_e * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END,
    snapshot_vitamin_b12 = CASE WHEN fi.vitamin_b12 IS NULL THEN NULL ELSE fi.vitamin_b12 * COALESCE(f.normalized_portion_grams, f.portion_size, 0) / 100.0 END
FROM food_items fi
WHERE f.food_id = fi.id
  AND (f.snapshot_fiber IS NULL
       OR f.snapshot_sugar IS NULL
       OR f.snapshot_saturated_fat IS NULL
       OR f.snapshot_sodium IS NULL
       OR f.snapshot_potassium IS NULL
       OR f.snapshot_cholesterol IS NULL
       OR f.snapshot_calcium IS NULL
       OR f.snapshot_iron IS NULL
       OR f.snapshot_magnesium IS NULL
       OR f.snapshot_zinc IS NULL
       OR f.snapshot_vitamin_a IS NULL
       OR f.snapshot_vitamin_c IS NULL
       OR f.snapshot_vitamin_d IS NULL
       OR f.snapshot_vitamin_e IS NULL
       OR f.snapshot_vitamin_b12 IS NULL);

ALTER TABLE recipes
    ADD COLUMN IF NOT EXISTS snapshot_saturated_fat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_potassium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_cholesterol DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_calcium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_iron DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_magnesium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_zinc DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_a DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_c DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_d DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_e DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_b12 DOUBLE PRECISION;

ALTER TABLE recipe_logs
    ADD COLUMN IF NOT EXISTS snapshot_saturated_fat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_potassium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_cholesterol DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_calcium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_iron DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_magnesium DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_zinc DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_a DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_c DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_d DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_e DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_vitamin_b12 DOUBLE PRECISION;

UPDATE recipe_logs rl
SET snapshot_saturated_fat = CASE WHEN r.snapshot_saturated_fat IS NULL THEN NULL ELSE r.snapshot_saturated_fat * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_potassium = CASE WHEN r.snapshot_potassium IS NULL THEN NULL ELSE r.snapshot_potassium * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_cholesterol = CASE WHEN r.snapshot_cholesterol IS NULL THEN NULL ELSE r.snapshot_cholesterol * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_calcium = CASE WHEN r.snapshot_calcium IS NULL THEN NULL ELSE r.snapshot_calcium * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_iron = CASE WHEN r.snapshot_iron IS NULL THEN NULL ELSE r.snapshot_iron * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_magnesium = CASE WHEN r.snapshot_magnesium IS NULL THEN NULL ELSE r.snapshot_magnesium * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_zinc = CASE WHEN r.snapshot_zinc IS NULL THEN NULL ELSE r.snapshot_zinc * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_vitamin_a = CASE WHEN r.snapshot_vitamin_a IS NULL THEN NULL ELSE r.snapshot_vitamin_a * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_vitamin_c = CASE WHEN r.snapshot_vitamin_c IS NULL THEN NULL ELSE r.snapshot_vitamin_c * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_vitamin_d = CASE WHEN r.snapshot_vitamin_d IS NULL THEN NULL ELSE r.snapshot_vitamin_d * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_vitamin_e = CASE WHEN r.snapshot_vitamin_e IS NULL THEN NULL ELSE r.snapshot_vitamin_e * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END,
    snapshot_vitamin_b12 = CASE WHEN r.snapshot_vitamin_b12 IS NULL THEN NULL ELSE r.snapshot_vitamin_b12 * COALESCE(rl.serving_grams, 0) / NULLIF(r.total_yield_grams, 0) END
FROM recipes r
WHERE rl.recipe_id = r.id
  AND r.total_yield_grams IS NOT NULL
  AND r.total_yield_grams > 0;
