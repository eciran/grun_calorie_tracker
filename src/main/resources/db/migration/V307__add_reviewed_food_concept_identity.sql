-- Additive only: no historical reclassification, rekey or merge is performed here.
ALTER TABLE food_items ADD COLUMN food_concept_key VARCHAR(80);
ALTER TABLE food_items ADD COLUMN composition_variant_key VARCHAR(80);
ALTER TABLE food_items ADD COLUMN source_data_type VARCHAR(32);

ALTER TABLE food_items ADD CONSTRAINT chk_food_concept_pair
    CHECK ((food_concept_key IS NULL AND composition_variant_key IS NULL)
        OR (food_concept_key IS NOT NULL AND composition_variant_key IS NOT NULL));

CREATE INDEX idx_food_items_concept_variant
    ON food_items (food_concept_key, composition_variant_key, preparation_state);
