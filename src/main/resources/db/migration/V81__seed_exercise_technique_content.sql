UPDATE exercise_items
SET setup_notes = COALESCE(setup_notes, 'Set up in a stable starting position and keep the movement controlled.'),
    breathing_tips = COALESCE(breathing_tips, 'Breathe steadily and avoid holding your breath through the full set.'),
    coaching_cues = COALESCE(coaching_cues, 'Move with control, keep posture stable, and stop if form breaks.'),
    common_mistakes = COALESCE(common_mistakes, 'Rushing the movement, losing alignment, or using a range of motion that causes discomfort.'),
    contraindications = COALESCE(contraindications, 'Avoid or modify if pain, dizziness, numbness, or injury symptoms are present.')
WHERE active = TRUE;

UPDATE exercise_items
SET technique_status = 'APPROVED'
WHERE met_code IN (
    'BODYWEIGHT_SQUAT',
    'PUSH_UP',
    'PLANK',
    'WALKING_GENERAL',
    'WALKING_BRISK',
    'RUNNING_GENERAL',
    'CYCLING_MODERATE',
    'DUMBBELL_DEADLIFT',
    'BENCH_PRESS'
)
  AND active = TRUE;