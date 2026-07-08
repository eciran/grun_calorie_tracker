CREATE TABLE IF NOT EXISTS recipe_import_candidates (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(160) NOT NULL,
    source_key VARCHAR(220) NOT NULL,
    source_title VARCHAR(255),
    source_url VARCHAR(1000),
    source_revision_url VARCHAR(1000),
    license VARCHAR(120),
    recommended_import_status VARCHAR(80),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    recipe_name VARCHAR(160) NOT NULL,
    meal_type VARCHAR(40),
    market_region VARCHAR(40),
    language VARCHAR(12),
    ingredient_count INTEGER,
    unresolved_ingredient_count INTEGER,
    validation_issues TEXT,
    raw_payload TEXT NOT NULL,
    created_recipe_id BIGINT,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    review_note VARCHAR(1000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_recipe_import_candidate_source UNIQUE (batch_id, source_key),
    CONSTRAINT fk_recipe_import_candidate_recipe FOREIGN KEY (created_recipe_id) REFERENCES recipes(id)
);

CREATE INDEX IF NOT EXISTS idx_recipe_import_candidates_status ON recipe_import_candidates(status);
CREATE INDEX IF NOT EXISTS idx_recipe_import_candidates_batch_id ON recipe_import_candidates(batch_id);
CREATE INDEX IF NOT EXISTS idx_recipe_import_candidates_created_at ON recipe_import_candidates(created_at);
