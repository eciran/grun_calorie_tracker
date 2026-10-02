ALTER TABLE food_items
    DROP CONSTRAINT IF EXISTS chk_food_items_catalog_type;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_catalog_type
        CHECK (catalog_type IS NULL OR catalog_type IN (
            'BRANDED_PRODUCT',
            'GENERIC_INGREDIENT',
            'LOCAL_DISH',
            'STANDARD_PREPARED_ITEM',
            'USER_CUSTOM'
        ));
