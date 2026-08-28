ALTER TABLE food_product_review_cases
    ADD COLUMN IF NOT EXISTS ai_nutrition_label_processing_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_nutrition_label_consent_version VARCHAR(50) NULL;
