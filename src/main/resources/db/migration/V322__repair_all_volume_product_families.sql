-- An active family whose every active member is packaged in milliliters is a
-- volume-native product family. This repairs legacy gram defaults while keeping
-- each physical product, barcode and package membership independent.
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
        AND member.package_unit = 'MILLILITER'
        AND NOT EXISTS (
            SELECT 1
            FROM food_product_family_members sibling
            WHERE sibling.family_id = family.id
              AND sibling.active = TRUE
              AND sibling.package_unit <> 'MILLILITER'
        )
  );
