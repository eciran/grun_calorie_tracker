ALTER TABLE food_item_serving_options
    ADD COLUMN user_visible BOOLEAN NOT NULL DEFAULT TRUE;

-- Keep source-backed conversion evidence, but hide measures that are unnatural
-- as primary mobile choices for whole produce.
UPDATE food_item_serving_options option
SET user_visible = FALSE
FROM food_items item
WHERE option.food_item_id = item.id
  AND option.unit_type = 'CUP'
  AND (
      (item.source_key = 'USDA_FOODDATA:fdc:746771' AND abs(option.gram_weight - 165) < 0.000001)
      OR (item.source_key = 'USDA_FOODDATA:fdc:325430' AND abs(option.gram_weight - 154) < 0.000001)
      OR (item.source_key = 'USDA_FOODDATA:fdc:169949' AND abs(option.gram_weight - 165) < 0.000001)
      OR (item.source_key = 'USDA_FOODDATA:fdc:168482' AND abs(option.gram_weight - 133) < 0.000001)
      OR (item.source_key = 'USDA_FOODDATA:fdc:321360' AND abs(option.gram_weight - 152) < 0.000001)
  );

CREATE INDEX idx_food_item_serving_options_mobile_visible
    ON food_item_serving_options(food_item_id, is_default DESC, label)
    WHERE user_visible = TRUE AND quality_status = 'VERIFIED';
