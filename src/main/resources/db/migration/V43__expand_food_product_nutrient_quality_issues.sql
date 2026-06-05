ALTER TABLE food_product_quality_issues
    DROP CONSTRAINT IF EXISTS chk_food_product_quality_issues_type;

ALTER TABLE food_product_quality_issues
    ADD CONSTRAINT chk_food_product_quality_issues_type
        CHECK (issue_type IN (
            'LOW_QUALITY',
            'MISSING_IMAGE',
            'MISSING_CALORIES',
            'MISSING_MACROS',
            'MISSING_SERVING_SIZE',
            'MISSING_REGION',
            'UNSUPPORTED_REGION',
            'MISSING_BARCODE',
            'INVALID_BARCODE_FORMAT',
            'SUSPICIOUS_CALORIES',
            'SUSPICIOUS_MACROS',
            'MISSING_MICRONUTRIENTS',
            'MISSING_NUTRIENT_QUALITY_FIELDS',
            'SUSPICIOUS_NUTRIENT_QUALITY'
        ));
