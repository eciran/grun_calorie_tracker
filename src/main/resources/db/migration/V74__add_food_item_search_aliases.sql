CREATE TABLE IF NOT EXISTS food_item_search_aliases (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
    alias VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    language VARCHAR(10) NOT NULL,
    alias_type VARCHAR(40) NOT NULL,
    source VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_food_item_search_aliases_item_alias_language
    ON food_item_search_aliases(food_item_id, normalized_alias, language);

CREATE INDEX IF NOT EXISTS idx_food_item_search_aliases_food_item
    ON food_item_search_aliases(food_item_id);

CREATE INDEX IF NOT EXISTS idx_food_item_search_aliases_language_active
    ON food_item_search_aliases(language, active);

CREATE INDEX IF NOT EXISTS idx_food_item_search_aliases_normalized_trgm
    ON food_item_search_aliases USING gin (normalized_alias gin_trgm_ops);

INSERT INTO food_item_search_aliases(food_item_id, alias, normalized_alias, language, alias_type, source)
SELECT id, alias, normalized_alias, language, alias_type, 'migration_seed'
FROM (
    SELECT id, 'süt' AS alias, 'sut' AS normalized_alias, 'TR' AS language, 'TRANSLATION' AS alias_type
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])milk([^a-z]|$)'
    UNION ALL
    SELECT id, 'yoğurt', 'yogurt', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])yog(h)?urt([^a-z]|$)'
    UNION ALL
    SELECT id, 'tavuk göğsü', 'tavuk gogsu', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) LIKE '%chicken breast%'
    UNION ALL
    SELECT id, 'ekmek', 'ekmek', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])bread([^a-z]|$)'
    UNION ALL
    SELECT id, 'pirinç', 'pirinc', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])rice([^a-z]|$)'
    UNION ALL
    SELECT id, 'peynir', 'peynir', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])cheese([^a-z]|$)'
    UNION ALL
    SELECT id, 'yumurta', 'yumurta', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])eggs?([^a-z]|$)'
    UNION ALL
    SELECT id, 'yulaf', 'yulaf', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])oats?([^a-z]|$)'
    UNION ALL
    SELECT id, 'tereyağı', 'tereyagi', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])butter([^a-z]|$)'
    UNION ALL
    SELECT id, 'mercimek', 'mercimek', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE lower(name) ~ '(^|[^a-z])lentils?([^a-z]|$)'
) aliases
ON CONFLICT (food_item_id, normalized_alias, language) DO NOTHING;