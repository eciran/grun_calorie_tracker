ALTER TABLE food_items
    DROP CONSTRAINT IF EXISTS chk_food_items_nutrition_reference_unit;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_nutrition_reference_unit
        CHECK (nutrition_reference_unit IN ('PER_100G', 'PER_100ML', 'PER_SERVING'));

ALTER TABLE food_item_serving_options
    DROP CONSTRAINT IF EXISTS chk_food_item_serving_options_weight_or_volume;

ALTER TABLE food_item_serving_options
    ADD CONSTRAINT chk_food_item_serving_options_weight_or_volume
        CHECK (
            (gram_weight IS NOT NULL AND gram_weight > 0)
            OR (ml_volume IS NOT NULL AND ml_volume > 0)
            OR (
                gram_weight IS NULL
                AND ml_volume IS NULL
                AND unit_type IN ('SERVING', 'PORTION', 'PIECE')
            )
        );

ALTER TABLE food_logs
    ADD COLUMN normalized_nutrition_reference_amount DOUBLE PRECISION;

ALTER TABLE meal_template_items
    ADD COLUMN normalized_nutrition_reference_amount DOUBLE PRECISION;

ALTER TABLE food_logs
    ADD CONSTRAINT chk_food_logs_normalized_reference_positive
        CHECK (normalized_nutrition_reference_amount IS NULL OR normalized_nutrition_reference_amount > 0);

ALTER TABLE meal_template_items
    ADD CONSTRAINT chk_meal_template_items_normalized_reference_positive
        CHECK (normalized_nutrition_reference_amount IS NULL OR normalized_nutrition_reference_amount > 0);
