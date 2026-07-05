ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS technique_review_note VARCHAR(1000);

UPDATE exercise_items
SET technique_review_note = COALESCE(technique_review_note, 'Initial technique metadata requires admin review before public technique library exposure.')
WHERE technique_status = 'NEEDS_REVIEW'
  AND active = TRUE;