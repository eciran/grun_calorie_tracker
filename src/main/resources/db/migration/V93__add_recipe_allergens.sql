CREATE TABLE IF NOT EXISTS recipe_allergens (
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    allergen VARCHAR(60) NOT NULL,
    PRIMARY KEY (recipe_id, allergen)
);

CREATE INDEX IF NOT EXISTS idx_recipe_allergens_allergen
    ON recipe_allergens(allergen);
