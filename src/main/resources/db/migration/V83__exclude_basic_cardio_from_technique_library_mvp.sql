UPDATE exercise_items
SET technique_status = 'NEEDS_REVIEW',
    technique_review_note = COALESCE(technique_review_note, 'Basic cardio items are excluded from the first technique library MVP public set.')
WHERE met_code IN (
    'WALKING_GENERAL',
    'WALKING_BRISK',
    'RUNNING_GENERAL',
    'CYCLING_MODERATE',
    'SWIMMING_FREESTYLE',
    'ROWING_MACHINE',
    'ELLIPTICAL_MODERATE',
    'STAIR_CLIMBER'
);