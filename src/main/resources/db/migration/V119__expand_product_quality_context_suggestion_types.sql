ALTER TABLE product_quality_suggestions DROP CONSTRAINT IF EXISTS chk_product_quality_suggestions_type;

ALTER TABLE product_quality_suggestions
    ADD CONSTRAINT chk_product_quality_suggestions_type
        CHECK (suggestion_type IN (
            'NAME_CLEANUP',
            'DISPLAY_NAME',
            'LOCALIZATION',
            'SEARCH_ALIAS',
            'SERVING_OPTION',
            'CANONICAL_DUPLICATE_REVIEW',
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