ALTER TABLE food_product_contributions
    ALTER COLUMN evidence_retrieved_at TYPE TIMESTAMP WITH TIME ZONE
    USING evidence_retrieved_at AT TIME ZONE 'UTC';
