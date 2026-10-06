WITH target_products AS (
    SELECT id
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND preparation_state = 'RAW'
      AND (
          canonical_food_key = 'GLOBAL:GENERIC_INGREDIENT:RAW:banana'
          OR source_key = 'GLOBAL:GENERIC_INGREDIENT:RAW:banana'
      )
), option_data(label, gram_weight, default_candidate) AS (
    VALUES
        ('1 small banana', 101.0::DOUBLE PRECISION, FALSE),
        ('1 medium banana', 118.0::DOUBLE PRECISION, TRUE),
        ('1 large banana', 136.0::DOUBLE PRECISION, FALSE)
)
INSERT INTO food_item_serving_options (
    food_item_id,
    label,
    unit_type,
    quantity,
    gram_weight,
    ml_volume,
    is_default,
    source,
    quality_status
)
SELECT
    target.id,
    option_data.label,
    'PIECE',
    1.0,
    option_data.gram_weight,
    NULL,
    option_data.default_candidate
        AND NOT EXISTS (
            SELECT 1
            FROM food_item_serving_options existing_default
            WHERE existing_default.food_item_id = target.id
              AND existing_default.is_default = TRUE
        ),
    'ADMIN',
    'VERIFIED'
FROM target_products target
CROSS JOIN option_data
WHERE NOT EXISTS (
    SELECT 1
    FROM food_item_serving_options existing
    WHERE existing.food_item_id = target.id
      AND existing.unit_type = 'PIECE'
      AND abs(existing.quantity - 1.0) < 0.000001
      AND abs(existing.gram_weight - option_data.gram_weight) < 0.000001
);

WITH target_products AS (
    SELECT id
    FROM food_items
    WHERE catalog_type = 'GENERIC_INGREDIENT'
      AND preparation_state = 'RAW'
      AND (
          canonical_food_key = 'GLOBAL:GENERIC_INGREDIENT:RAW:banana'
          OR source_key = 'GLOBAL:GENERIC_INGREDIENT:RAW:banana'
      )
), localized_labels(gram_weight, language, label) AS (
    VALUES
        (101.0::DOUBLE PRECISION, 'EN', '1 small banana'),
        (101.0::DOUBLE PRECISION, 'TR', '1 küçük boy muz'),
        (118.0::DOUBLE PRECISION, 'EN', '1 medium banana'),
        (118.0::DOUBLE PRECISION, 'TR', '1 orta boy muz'),
        (136.0::DOUBLE PRECISION, 'EN', '1 large banana'),
        (136.0::DOUBLE PRECISION, 'TR', '1 büyük boy muz')
)
INSERT INTO food_item_serving_option_localizations (
    serving_option_id,
    language,
    label,
    source,
    active,
    created_at,
    updated_at
)
SELECT
    serving_option.id,
    localized_labels.language,
    localized_labels.label,
    'migration-v302',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM target_products target
JOIN food_item_serving_options serving_option
  ON serving_option.food_item_id = target.id
 AND serving_option.unit_type = 'PIECE'
 AND abs(serving_option.quantity - 1.0) < 0.000001
JOIN localized_labels
  ON abs(localized_labels.gram_weight - serving_option.gram_weight) < 0.000001
ON CONFLICT (serving_option_id, language)
DO UPDATE SET
    label = EXCLUDED.label,
    source = EXCLUDED.source,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

