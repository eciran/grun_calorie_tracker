ALTER TABLE recipe_ingredients
    ALTER COLUMN food_item_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS snapshot_food_name VARCHAR(220),
    ADD COLUMN IF NOT EXISTS snapshot_calories DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_protein DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_carbs DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS snapshot_fat DOUBLE PRECISION,
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

ALTER TABLE recipe_ingredients
    DROP CONSTRAINT IF EXISTS chk_recipe_ingredients_food_or_snapshot;

ALTER TABLE recipe_ingredients
    ADD CONSTRAINT chk_recipe_ingredients_food_or_snapshot
    CHECK (food_item_id IS NOT NULL OR NULLIF(TRIM(snapshot_food_name), '') IS NOT NULL);
