ALTER TABLE food_items
    ADD COLUMN publication_status VARCHAR(30);

UPDATE food_items
SET publication_status = CASE
    WHEN COALESCE(is_custom, FALSE) = TRUE THEN 'PRIVATE_USER'
    WHEN verification_status = 'REJECTED' THEN 'HIDDEN'
    ELSE 'PUBLISHED'
END
WHERE publication_status IS NULL;

ALTER TABLE food_items
    ALTER COLUMN publication_status SET DEFAULT 'PUBLISHED',
    ALTER COLUMN publication_status SET NOT NULL;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_publication_status
        CHECK (publication_status IN ('INTERNAL_REVIEW', 'PUBLISHED', 'PRIVATE_USER', 'HIDDEN'));

CREATE INDEX idx_food_items_publication_status
    ON food_items (publication_status);

CREATE UNIQUE INDEX uq_food_items_internal_candidate_barcode_market
    ON food_items (normalized_barcode, market_region)
    WHERE publication_status = 'INTERNAL_REVIEW'
      AND normalized_barcode IS NOT NULL;
