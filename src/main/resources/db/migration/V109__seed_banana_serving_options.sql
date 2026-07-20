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
    fi.id,
    option_data.label,
    'PIECE',
    option_data.quantity,
    option_data.gram_weight,
    NULL,
    option_data.is_default
        AND NOT EXISTS (
            SELECT 1
            FROM food_item_serving_options existing_default
            WHERE existing_default.food_item_id = fi.id
              AND existing_default.is_default = TRUE
        ),
    'ADMIN',
    'VERIFIED'
FROM food_items fi
CROSS JOIN (
    VALUES
        ('1 medium banana', 1.0::DOUBLE PRECISION, 118.0::DOUBLE PRECISION, TRUE),
        ('1 small banana', 1.0::DOUBLE PRECISION, 101.0::DOUBLE PRECISION, FALSE),
        ('1 large banana', 1.0::DOUBLE PRECISION, 136.0::DOUBLE PRECISION, FALSE),
        ('1/2 banana', 0.5::DOUBLE PRECISION, 59.0::DOUBLE PRECISION, FALSE),
        ('1/4 banana', 0.25::DOUBLE PRECISION, 30.0::DOUBLE PRECISION, FALSE)
) AS option_data(label, quantity, gram_weight, is_default)
WHERE lower(coalesce(fi.display_name, fi.name)) = 'banana'
  AND fi.catalog_type IN ('GENERIC_INGREDIENT', 'BRANDED_PRODUCT')
  AND NOT EXISTS (
      SELECT 1
      FROM food_item_serving_options existing
      WHERE existing.food_item_id = fi.id
        AND lower(existing.label) = lower(option_data.label)
  );
