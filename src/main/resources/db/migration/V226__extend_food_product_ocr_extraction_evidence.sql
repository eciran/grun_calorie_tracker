ALTER TABLE food_product_review_case_extractions
    ADD COLUMN word_boxes_json TEXT,
    ADD COLUMN field_evidence_json TEXT,
    ADD COLUMN quality_signals_json TEXT,
    ADD COLUMN decisions_json TEXT,
    ADD COLUMN correction_audit_json TEXT;
