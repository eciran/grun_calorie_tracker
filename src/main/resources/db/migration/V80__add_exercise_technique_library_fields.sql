ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS environment VARCHAR(20),
    ADD COLUMN IF NOT EXISTS movement_pattern VARCHAR(120),
    ADD COLUMN IF NOT EXISTS setup_notes VARCHAR(1500),
    ADD COLUMN IF NOT EXISTS breathing_tips VARCHAR(1500),
    ADD COLUMN IF NOT EXISTS common_mistakes VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS coaching_cues VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS contraindications VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS technique_status VARCHAR(30);

UPDATE exercise_items
SET environment = COALESCE(environment, 'GYM_HOME'),
    technique_status = COALESCE(technique_status, 'NEEDS_REVIEW')
WHERE active = TRUE;

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'CARDIO')
WHERE met_code IN ('WALKING_GENERAL', 'WALKING_BRISK', 'RUNNING_GENERAL', 'CYCLING_MODERATE', 'SWIMMING_FREESTYLE', 'ROWING_MACHINE', 'ELLIPTICAL_MODERATE', 'STAIR_CLIMBER');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'SQUAT_LUNGE')
WHERE met_code IN ('BODYWEIGHT_SQUAT', 'BODYWEIGHT_LUNGE', 'LEG_PRESS');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'HINGE')
WHERE met_code IN ('DUMBBELL_DEADLIFT', 'KETTLEBELL_SWING');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'PUSH')
WHERE met_code IN ('PUSH_UP', 'BENCH_PRESS', 'DUMBBELL_SHOULDER_PRESS', 'TRICEP_DIP');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'PULL')
WHERE met_code IN ('PULL_UP', 'LAT_PULLDOWN', 'DUMBBELL_BICEP_CURL');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'CORE')
WHERE met_code IN ('PLANK', 'SIT_UP', 'CRUNCH', 'MOUNTAIN_CLIMBER', 'PILATES_GENERAL', 'YOGA_HATHA');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'MOBILITY')
WHERE met_code IN ('MOBILITY_STRETCHING');

UPDATE exercise_items
SET movement_pattern = COALESCE(movement_pattern, 'FULL_BODY')
WHERE movement_pattern IS NULL;