UPDATE exercise_items
SET technique_review_status = 'APPROVED',
    technique_review_note = 'Legacy curated catalog baseline.',
    technique_reviewed_by = 'SYSTEM_MIGRATION',
    technique_reviewed_at = CURRENT_TIMESTAMP
WHERE technique_review_status = 'PENDING'
  AND technique_reviewed_at IS NULL;
