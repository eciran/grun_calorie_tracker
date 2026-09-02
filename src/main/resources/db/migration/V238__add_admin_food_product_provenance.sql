ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS admin_source_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS admin_source_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS admin_creation_note VARCHAR(500);
