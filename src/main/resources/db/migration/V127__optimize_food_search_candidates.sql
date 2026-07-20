CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_food_items_display_name_trgm
    ON food_items USING gin (lower(display_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_food_items_short_display_name_trgm
    ON food_items USING gin (lower(short_display_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_food_items_brand_trgm
    ON food_items USING gin (lower(brand) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_food_item_aliases_alias_lower_trgm
    ON food_item_search_aliases USING gin (lower(alias) gin_trgm_ops)
    WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_food_item_aliases_normalized_lower_trgm
    ON food_item_search_aliases USING gin (lower(normalized_alias) gin_trgm_ops)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_food_item_localizations_display_lower_trgm
    ON food_item_localizations USING gin (lower(display_name) gin_trgm_ops)
    WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_food_item_localizations_short_lower_trgm
    ON food_item_localizations USING gin (lower(short_display_name) gin_trgm_ops)
    WHERE active = true;
