-- Product-family volume metadata plus a verified mass-volume option is stronger
-- evidence than a legacy gram default. Every package in the active family must
-- be volume-based; mixed-unit families remain unchanged for review.
UPDATE food_items item
SET nutrition_reference_unit = 'PER_100ML',
    serving_unit = 'MILLILITER',
    serving_size_grams = NULL
WHERE item.nutrition_reference_unit = 'PER_100G'
  AND EXISTS (
      SELECT 1
      FROM food_product_family_members member
      JOIN food_product_families family ON family.id = member.family_id
      WHERE member.food_item_id = item.id
        AND family.active = TRUE
        AND member.active = TRUE
        AND NOT EXISTS (
            SELECT 1
            FROM food_product_family_members sibling
            WHERE sibling.family_id = family.id
              AND sibling.active = TRUE
              AND sibling.package_unit <> 'MILLILITER'
        )
        AND EXISTS (
            SELECT 1
            FROM food_item_serving_options bridge
            WHERE bridge.food_item_id = family.representative_food_item_id
              AND bridge.quality_status = 'VERIFIED'
              AND bridge.user_visible = TRUE
              AND bridge.gram_weight > 0
              AND bridge.ml_volume > 0
        )
  );
