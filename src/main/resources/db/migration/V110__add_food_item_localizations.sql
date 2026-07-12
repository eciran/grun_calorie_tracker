CREATE TABLE IF NOT EXISTS food_item_localizations (
    id BIGSERIAL PRIMARY KEY,
    food_item_id BIGINT NOT NULL REFERENCES food_items(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    short_display_name VARCHAR(255),
    source VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_food_item_localizations_item_language
    ON food_item_localizations(food_item_id, language);

CREATE INDEX IF NOT EXISTS idx_food_item_localizations_language_active
    ON food_item_localizations(language, active);

CREATE INDEX IF NOT EXISTS idx_food_item_localizations_food_item
    ON food_item_localizations(food_item_id);

CREATE INDEX IF NOT EXISTS idx_food_item_localizations_display_trgm
    ON food_item_localizations USING gin (display_name gin_trgm_ops);

WITH localization_seed AS (
    SELECT id AS food_item_id, 'TR' AS language, 'Muz' AS display_name, 'Muz' AS short_display_name
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%banana%'
    UNION ALL
    SELECT id, 'TR', 'Elma', 'Elma'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%apple%'
    UNION ALL
    SELECT id, 'TR', 'Brokoli', 'Brokoli'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%broccoli%'
      AND lower(coalesce(short_display_name, display_name, name)) NOT LIKE '%raab%'
      AND lower(coalesce(short_display_name, display_name, name)) NOT LIKE '%rabe%'
      AND lower(coalesce(short_display_name, display_name, name)) NOT LIKE '%leaves%'
      AND lower(coalesce(short_display_name, display_name, name)) NOT LIKE '%stalk%'
    UNION ALL
    SELECT id, 'TR', 'Yumurta', 'Yumurta'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])eggs?([^a-z]|$)'
    UNION ALL
    SELECT id, 'TR', 'Tavuk Gogsu', 'Tavuk Gogsu'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%chicken breast%'
    UNION ALL
    SELECT id, 'TR', 'Pismis Beyaz Pirinc', 'Pismis Pirinc'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%rice%'
      AND preparation_state = 'COOKED'
    UNION ALL
    SELECT id, 'TR', 'Pirinc', 'Pirinc'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%rice%'
      AND (preparation_state IS NULL OR preparation_state <> 'COOKED')
    UNION ALL
    SELECT id, 'TR', 'Sut', 'Sut'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])milk([^a-z]|$)'
    UNION ALL
    SELECT id, 'TR', 'Yogurt', 'Yogurt'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])yog(h)?urt([^a-z]|$)'
    UNION ALL
    SELECT id, 'TR', 'Patates', 'Patates'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%potato%'
)
INSERT INTO food_item_localizations(food_item_id, language, display_name, short_display_name, source)
SELECT food_item_id, language, display_name, short_display_name, 'core_seed'
FROM localization_seed
ON CONFLICT (food_item_id, language) DO NOTHING;

WITH alias_seed AS (
    SELECT id AS food_item_id, 'muz' AS alias, 'muz' AS normalized_alias, 'TR' AS language, 'TRANSLATION' AS alias_type
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%banana%'
    UNION ALL
    SELECT id, 'banan', 'banan', 'TR', 'COMMON_NAME'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%banana%'
    UNION ALL
    SELECT id, 'elma', 'elma', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%apple%'
    UNION ALL
    SELECT id, 'brokoli', 'brokoli', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%broccoli%'
    UNION ALL
    SELECT id, 'yumurta', 'yumurta', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])eggs?([^a-z]|$)'
    UNION ALL
    SELECT id, 'tavuk gogsu', 'tavuk gogsu', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%chicken breast%'
    UNION ALL
    SELECT id, 'pirinc', 'pirinc', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%rice%'
    UNION ALL
    SELECT id, 'pilav', 'pilav', 'TR', 'SYNONYM'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%rice%'
      AND preparation_state = 'COOKED'
    UNION ALL
    SELECT id, 'sut', 'sut', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])milk([^a-z]|$)'
    UNION ALL
    SELECT id, 'yogurt', 'yogurt', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) ~ '(^|[^a-z])yog(h)?urt([^a-z]|$)'
    UNION ALL
    SELECT id, 'patates', 'patates', 'TR', 'TRANSLATION'
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND lower(coalesce(short_display_name, display_name, name)) LIKE '%potato%'
)
INSERT INTO food_item_search_aliases(food_item_id, alias, normalized_alias, language, alias_type, source)
SELECT food_item_id, alias, normalized_alias, language, alias_type, 'core_localization_seed'
FROM alias_seed
ON CONFLICT (food_item_id, normalized_alias, language) DO NOTHING;