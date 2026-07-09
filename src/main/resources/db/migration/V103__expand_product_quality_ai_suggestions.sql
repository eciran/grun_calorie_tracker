ALTER TABLE product_quality_suggestions
    ADD COLUMN IF NOT EXISTS field_name VARCHAR(100);

ALTER TABLE product_quality_suggestions DROP CONSTRAINT IF EXISTS chk_product_quality_suggestions_type;

ALTER TABLE product_quality_suggestions
    ADD CONSTRAINT chk_product_quality_suggestions_type
        CHECK (suggestion_type IN (
            'NAME_CLEANUP',
            'SEARCH_ALIAS',
            'MISSING_MACRO_DATA',
            'MISSING_MICRO_DATA',
            'SUSPICIOUS_CALORIE_VALUE',
            'MACRO_CALORIE_MISMATCH',
            'SUSPICIOUS_SODIUM_VALUE',
            'MISSING_SERVING_SIZE',
            'SOURCE_CONFLICT',
            'IMAGE_REVIEW_REQUIRED',
            'REGION_MISMATCH',
            'LABEL_REVIEW_REQUIRED'
        ));

DROP INDEX IF EXISTS ux_product_quality_suggestions_open_dedupe;

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_quality_suggestions_open_dedupe
    ON product_quality_suggestions (food_item_id, suggestion_type, COALESCE(field_name, ''), COALESCE(suggested_value, ''))
    WHERE status = 'OPEN';
