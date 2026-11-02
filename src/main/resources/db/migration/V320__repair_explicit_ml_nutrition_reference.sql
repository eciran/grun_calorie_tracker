-- Legacy imports may explicitly identify a liquid serving in milliliters while
-- retaining the old default PER_100G nutrition basis. Align only those records;
-- names, nutrition values, barcodes, source keys and package metadata stay intact.
UPDATE food_items
SET nutrition_reference_unit = 'PER_100ML',
    serving_unit = 'MILLILITER',
    serving_size_grams = NULL
WHERE nutrition_reference_unit = 'PER_100G'
  AND lower(trim(serving_unit)) IN (
      'ml',
      'milliliter',
      'milliliters',
      'millilitre',
      'millilitres'
  );

