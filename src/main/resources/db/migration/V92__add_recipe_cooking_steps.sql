CREATE TABLE IF NOT EXISTS recipe_cooking_steps (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    instruction VARCHAR(1000) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recipe_cooking_steps_recipe_order
    ON recipe_cooking_steps(recipe_id, step_order, id);