ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS short_display_name VARCHAR(255);

UPDATE food_items
SET display_name = COALESCE(display_name, name),
    short_display_name = COALESCE(short_display_name, display_name, name)
WHERE display_name IS NULL
   OR short_display_name IS NULL;
