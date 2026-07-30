ALTER TABLE food_product_source_evidence
    DROP CONSTRAINT IF EXISTS chk_food_source_evidence_provider;

ALTER TABLE food_product_source_evidence
    ADD CONSTRAINT chk_food_source_evidence_provider CHECK (provider IN (
        'MANUAL', 'OPEN_FOOD_FACTS', 'USDA_FOODDATA', 'EDAMAM', 'NUTRITIONIX',
        'LOCAL_CURATED', 'ADMIN_IMPORT', 'USER_SUBMITTED_LABEL', 'ADMIN_REVIEWED_LABEL'
    ));