ALTER TABLE product_nutrition_ocr_shadow_runs
    ADD COLUMN reconciliation_json TEXT NOT NULL DEFAULT '{}';
