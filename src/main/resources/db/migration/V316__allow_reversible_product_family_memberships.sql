ALTER TABLE food_product_family_members
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE food_product_family_members
    DROP CONSTRAINT uq_food_product_family_member_product;

CREATE UNIQUE INDEX uq_food_product_family_member_active_product
    ON food_product_family_members(food_item_id) WHERE active = TRUE;
