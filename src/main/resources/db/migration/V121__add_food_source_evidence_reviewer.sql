ALTER TABLE food_product_source_evidence
    ADD COLUMN reviewer_identity VARCHAR(255);

CREATE INDEX idx_food_source_evidence_reviewer
    ON food_product_source_evidence (reviewer_identity)
    WHERE reviewer_identity IS NOT NULL;
