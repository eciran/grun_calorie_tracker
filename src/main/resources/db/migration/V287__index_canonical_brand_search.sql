CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_food_brands_active_canonical_name_trgm
    ON food_brands USING gin (lower(canonical_name) gin_trgm_ops)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_food_brands_active_normalized_key_trgm
    ON food_brands USING gin (normalized_key gin_trgm_ops)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_food_brand_aliases_normalized_alias_trgm
    ON food_brand_aliases USING gin (normalized_alias gin_trgm_ops);
