ALTER TABLE ai_operations_policy
    ADD COLUMN active_photo_model VARCHAR(120),
    ADD COLUMN previous_photo_model VARCHAR(120);

UPDATE ai_operations_policy
SET active_photo_model = active_model
WHERE active_photo_model IS NULL;
