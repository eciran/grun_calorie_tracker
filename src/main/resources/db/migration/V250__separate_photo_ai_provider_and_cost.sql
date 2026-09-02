ALTER TABLE ai_operations_policy
    ADD COLUMN active_photo_provider VARCHAR(30),
    ADD COLUMN previous_photo_provider VARCHAR(30);

UPDATE ai_operations_policy
SET active_photo_provider = 'OPENAI',
    active_photo_model = 'gpt-5.6-terra'
WHERE active_photo_provider IS NULL;
