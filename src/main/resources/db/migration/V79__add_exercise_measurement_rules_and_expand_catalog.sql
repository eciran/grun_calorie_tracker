ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS default_measurement_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS allowed_measurement_types VARCHAR(255);

UPDATE exercise_items
SET default_measurement_type = 'DISTANCE',
    allowed_measurement_types = 'DURATION,DISTANCE,MIXED'
WHERE met_code IN ('WALKING_BRISK', 'RUNNING_GENERAL', 'CYCLING_MODERATE', 'SWIMMING_FREESTYLE', 'ROWING_MACHINE');

UPDATE exercise_items
SET default_measurement_type = 'DURATION',
    allowed_measurement_types = 'DURATION'
WHERE met_code IN ('PLANK', 'YOGA_HATHA');

UPDATE exercise_items
SET default_measurement_type = 'SETS_REPS',
    allowed_measurement_types = 'SETS_REPS,REPS,DURATION'
WHERE met_code IN ('BODYWEIGHT_SQUAT', 'PUSH_UP', 'JUMP_ROPE');

UPDATE exercise_items
SET default_measurement_type = 'WEIGHT_REPS',
    allowed_measurement_types = 'WEIGHT_REPS,SETS_REPS'
WHERE met_code IN ('DUMBBELL_DEADLIFT', 'BENCH_PRESS');

UPDATE exercise_items
SET default_measurement_type = COALESCE(default_measurement_type, 'DURATION'),
    allowed_measurement_types = COALESCE(allowed_measurement_types, 'DURATION')
WHERE default_measurement_type IS NULL
   OR allowed_measurement_types IS NULL;

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Walking', 'WALKING_GENERAL', 3.5, 'General casual walking.', NULL, 'Lower Body', 'Glutes, Hamstrings, Calves', 'None', 'BEGINNER', 'Walk at a comfortable pace with relaxed shoulders and steady breathing.', 'Use supportive footwear and slow down if joint discomfort appears.', NULL, NULL, NULL, 'DURATION', 'DURATION,DISTANCE,MIXED', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'WALKING_GENERAL');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Elliptical Trainer', 'ELLIPTICAL_MODERATE', 7.0, 'Moderate intensity elliptical cardio.', NULL, 'Full Body', 'Glutes, Quadriceps, Hamstrings, Shoulders', 'Elliptical', 'BEGINNER', 'Keep posture tall, push and pull handles smoothly, and maintain an even cadence.', 'Reduce resistance if knee or hip discomfort appears.', NULL, NULL, NULL, 'DURATION', 'DURATION,DISTANCE,MIXED', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'ELLIPTICAL_MODERATE');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Stair Climber', 'STAIR_CLIMBER', 8.8, 'Stair climbing machine cardio.', NULL, 'Lower Body', 'Glutes, Quadriceps, Calves', 'Stair Climber', 'INTERMEDIATE', 'Keep hands light on the rails, step through the full foot, and maintain controlled pace.', 'Avoid leaning heavily on rails and reduce intensity if knees hurt.', NULL, NULL, NULL, 'DURATION', 'DURATION,DISTANCE,MIXED', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'STAIR_CLIMBER');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Lunge', 'BODYWEIGHT_LUNGE', 5.5, 'Bodyweight single-leg lower-body movement.', NULL, 'Quadriceps', 'Glutes, Hamstrings, Core', 'None', 'BEGINNER', 'Step forward or backward, lower under control, then drive through the front foot to stand.', 'Keep the front knee tracking over toes and avoid collapsing inward.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'BODYWEIGHT_LUNGE');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Sit-Up', 'SIT_UP', 4.5, 'Bodyweight abdominal flexion exercise.', NULL, 'Core', 'Hip Flexors', 'None', 'BEGINNER', 'Start lying down, brace the core, curl up under control, then lower slowly.', 'Avoid pulling the neck and stop if lower back pain appears.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'SIT_UP');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Crunch', 'CRUNCH', 4.0, 'Short-range abdominal exercise.', NULL, 'Core', 'Upper Abs', 'None', 'BEGINNER', 'Lift shoulders from the floor using the abs, pause briefly, then lower under control.', 'Keep the neck neutral and avoid yanking the head forward.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'CRUNCH');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Pull-Up', 'PULL_UP', 7.0, 'Bodyweight upper-body pulling movement.', NULL, 'Back', 'Biceps, Shoulders, Core', 'Pull-Up Bar', 'INTERMEDIATE', 'Hang with active shoulders, pull chest toward the bar, then lower under control.', 'Use assistance if form breaks and avoid swinging excessively.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'PULL_UP');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Burpee', 'BURPEE', 10.0, 'High intensity full-body conditioning movement.', NULL, 'Full Body', 'Chest, Legs, Core, Shoulders', 'None', 'INTERMEDIATE', 'Squat down, step or jump to plank, return feet under hips, then stand or jump.', 'Scale the jump or push-up if fatigue causes form loss.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'BURPEE');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Mountain Climber', 'MOUNTAIN_CLIMBER', 8.0, 'Bodyweight core and conditioning drill.', NULL, 'Core', 'Shoulders, Hip Flexors', 'None', 'INTERMEDIATE', 'Hold a plank and drive knees toward the chest in a controlled alternating rhythm.', 'Keep hips stable and slow down if shoulders or wrists hurt.', NULL, NULL, NULL, 'DURATION', 'DURATION,REPS,SETS_REPS', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'MOUNTAIN_CLIMBER');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Dumbbell Shoulder Press', 'DUMBBELL_SHOULDER_PRESS', 5.8, 'Weighted overhead pressing movement.', NULL, 'Shoulders', 'Triceps, Core', 'Dumbbells', 'INTERMEDIATE', 'Brace the core, press dumbbells overhead, then lower under control to shoulder level.', 'Avoid excessive lower-back arching and start with manageable weight.', NULL, NULL, NULL, 'WEIGHT_REPS', 'WEIGHT_REPS,SETS_REPS', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'DUMBBELL_SHOULDER_PRESS');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Bicep Curl', 'DUMBBELL_BICEP_CURL', 4.0, 'Weighted elbow flexion exercise.', NULL, 'Biceps', 'Forearms', 'Dumbbells', 'BEGINNER', 'Keep elbows near the ribs, curl under control, then lower without swinging.', 'Use lighter weight if the torso starts rocking.', NULL, NULL, NULL, 'WEIGHT_REPS', 'WEIGHT_REPS,SETS_REPS', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'DUMBBELL_BICEP_CURL');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Tricep Dip', 'TRICEP_DIP', 5.0, 'Bodyweight triceps exercise using a bench or bars.', NULL, 'Triceps', 'Chest, Shoulders', 'Bench or Dip Bars', 'INTERMEDIATE', 'Lower with elbows tracking back, then press up while keeping shoulders controlled.', 'Limit depth if shoulders feel pinched.', NULL, NULL, NULL, 'SETS_REPS', 'SETS_REPS,REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'TRICEP_DIP');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Lat Pulldown', 'LAT_PULLDOWN', 5.2, 'Machine upper-body pulling movement.', NULL, 'Back', 'Biceps, Rear Shoulders', 'Cable Machine', 'BEGINNER', 'Pull the bar toward the upper chest, keep ribs down, and return under control.', 'Avoid pulling behind the neck and avoid using momentum.', NULL, NULL, NULL, 'WEIGHT_REPS', 'WEIGHT_REPS,SETS_REPS', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'LAT_PULLDOWN');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Leg Press', 'LEG_PRESS', 5.8, 'Machine lower-body pressing movement.', NULL, 'Quadriceps', 'Glutes, Hamstrings', 'Leg Press Machine', 'BEGINNER', 'Place feet securely, lower under control, then press without locking the knees.', 'Keep the lower back against the pad and avoid excessive depth.', NULL, NULL, NULL, 'WEIGHT_REPS', 'WEIGHT_REPS,SETS_REPS', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'LEG_PRESS');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Kettlebell Swing', 'KETTLEBELL_SWING', 9.0, 'Explosive hip hinge conditioning exercise.', NULL, 'Glutes', 'Hamstrings, Core, Back', 'Kettlebell', 'INTERMEDIATE', 'Hinge at the hips, drive the kettlebell forward with hip power, and keep arms relaxed.', 'Do not squat the movement and stop if lower-back position cannot be maintained.', NULL, NULL, NULL, 'WEIGHT_REPS', 'WEIGHT_REPS,SETS_REPS,DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'KETTLEBELL_SWING');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Pilates', 'PILATES_GENERAL', 3.5, 'Low to moderate intensity mat Pilates session.', NULL, 'Core', 'Hips, Glutes, Shoulders', 'Mat', 'BEGINNER', 'Move slowly with controlled breathing and maintain alignment through each movement.', 'Modify ranges that cause pain or excessive strain.', NULL, NULL, NULL, 'DURATION', 'DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'PILATES_GENERAL');

INSERT INTO exercise_items (name, met_code, calories_per_minute, description, icon_url, primary_muscle_group, secondary_muscle_groups, equipment, difficulty, instructions, safety_notes, thumbnail_url, video_url, animation_url, default_measurement_type, allowed_measurement_types, ai_eligible, active)
SELECT 'Mobility Stretching', 'MOBILITY_STRETCHING', 2.5, 'Gentle stretching and mobility work.', NULL, 'Full Body', 'Hips, Shoulders, Spine', 'Mat', 'BEGINNER', 'Move through comfortable ranges, breathe slowly, and avoid forcing end positions.', 'Stretching should not create sharp pain or numbness.', NULL, NULL, NULL, 'DURATION', 'DURATION', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'MOBILITY_STRETCHING');