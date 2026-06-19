CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_food_items_name_trgm
    ON food_items USING gin (lower(name) gin_trgm_ops);
