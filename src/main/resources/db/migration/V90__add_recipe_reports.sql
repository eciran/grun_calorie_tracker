CREATE TABLE IF NOT EXISTS recipe_reports (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,
    note VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recipe_report_user_recipe_status UNIQUE (user_id, recipe_id, status)
);

CREATE INDEX IF NOT EXISTS idx_recipe_reports_status_created
    ON recipe_reports(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recipe_reports_recipe
    ON recipe_reports(recipe_id);
