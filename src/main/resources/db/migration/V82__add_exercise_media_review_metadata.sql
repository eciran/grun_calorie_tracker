ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS animation_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS media_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS media_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS media_review_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS media_reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS media_reviewed_by VARCHAR(255);

UPDATE exercise_items
SET animation_type = COALESCE(animation_type, CASE
        WHEN animation_url IS NOT NULL AND animation_url <> '' THEN 'ANIMATION'
        WHEN video_url IS NOT NULL AND video_url <> '' THEN 'VIDEO'
        WHEN thumbnail_url IS NOT NULL AND thumbnail_url <> '' THEN 'THUMBNAIL'
        ELSE 'NONE'
    END),
    media_status = COALESCE(media_status, CASE
        WHEN thumbnail_url IS NOT NULL OR video_url IS NOT NULL OR animation_url IS NOT NULL THEN 'NEEDS_REVIEW'
        ELSE 'MISSING'
    END),
    media_source = COALESCE(media_source, CASE
        WHEN thumbnail_url IS NOT NULL OR video_url IS NOT NULL OR animation_url IS NOT NULL THEN 'ADMIN_SEED'
        ELSE 'NONE'
    END)
WHERE active = TRUE;