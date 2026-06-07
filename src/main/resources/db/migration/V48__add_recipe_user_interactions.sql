CREATE TABLE IF NOT EXISTS recipe_user_interactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    saved BOOLEAN NOT NULL DEFAULT FALSE,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    rating INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recipe_user_interaction UNIQUE (user_id, recipe_id),
    CONSTRAINT chk_recipe_user_interactions_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_recipe_user_interactions_user_saved
    ON recipe_user_interactions(user_id, saved, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_recipe_user_interactions_user_favorite
    ON recipe_user_interactions(user_id, favorite, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_recipe_user_interactions_recipe
    ON recipe_user_interactions(recipe_id);
