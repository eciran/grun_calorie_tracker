ALTER TABLE meal_template_items
    ADD COLUMN serving_option_id BIGINT,
    ADD COLUMN normalized_portion_milliliters DOUBLE PRECISION;

ALTER TABLE meal_template_items
    ADD CONSTRAINT fk_meal_template_items_serving_option
        FOREIGN KEY (serving_option_id)
        REFERENCES food_item_serving_options(id)
        ON DELETE SET NULL;

ALTER TABLE meal_template_items
    ADD CONSTRAINT chk_meal_template_items_normalized_ml_positive
        CHECK (normalized_portion_milliliters IS NULL OR normalized_portion_milliliters > 0);
