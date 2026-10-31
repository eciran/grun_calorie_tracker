-- Keep reverted family rows as audit history while allowing a corrected active family to reuse the key.
ALTER TABLE food_product_families
    DROP CONSTRAINT uq_food_product_family_key;

CREATE UNIQUE INDEX uq_food_product_family_active_key
    ON food_product_families(lower(family_key)) WHERE active = TRUE;
