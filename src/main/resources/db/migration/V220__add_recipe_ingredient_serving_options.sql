ALTER TABLE recipe_ingredients
    ADD COLUMN serving_option_id BIGINT NULL,
    ADD COLUMN normalized_portion_milliliters DOUBLE PRECISION NULL;

ALTER TABLE recipe_ingredients
    ADD CONSTRAINT fk_recipe_ingredients_serving_option
        FOREIGN KEY (serving_option_id)
        REFERENCES food_item_serving_options (id)
        ON DELETE SET NULL;

ALTER TABLE recipe_ingredients
    ADD CONSTRAINT chk_recipe_ingredients_normalized_milliliters_positive
        CHECK (normalized_portion_milliliters IS NULL OR normalized_portion_milliliters > 0);

CREATE INDEX idx_recipe_ingredients_serving_option_id
    ON recipe_ingredients (serving_option_id);
