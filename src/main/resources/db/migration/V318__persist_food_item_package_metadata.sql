ALTER TABLE food_items
    ADD COLUMN package_quantity NUMERIC(12,3),
    ADD COLUMN package_unit VARCHAR(24),
    ADD CONSTRAINT chk_food_item_package_metadata_pair CHECK (
        (package_quantity IS NULL AND package_unit IS NULL)
        OR (package_quantity > 0 AND package_unit IN ('GRAM', 'MILLILITER'))
    );

CREATE INDEX idx_food_item_package_metadata
    ON food_items(package_unit, package_quantity)
    WHERE package_quantity IS NOT NULL;
