UPDATE exercise_items
SET ai_eligible = TRUE
WHERE ai_eligible IS NULL;

UPDATE exercise_items
SET active = TRUE
WHERE active IS NULL;

INSERT INTO exercise_items (
    name,
    met_code,
    calories_per_minute,
    description,
    icon_url,
    primary_muscle_group,
    secondary_muscle_groups,
    equipment,
    difficulty,
    instructions,
    safety_notes,
    thumbnail_url,
    video_url,
    animation_url,
    ai_eligible,
    active
)
SELECT
    'Brisk Walking',
    'WALKING_BRISK',
    4.8,
    'Moderate pace walking for general conditioning.',
    NULL,
    'Lower Body',
    'Glutes, Hamstrings, Calves',
    'None',
    'BEGINNER',
    'Keep an upright posture, swing arms naturally, and maintain a pace that raises breathing without forcing it.',
    'Use comfortable shoes and reduce pace if joint pain or dizziness occurs.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'WALKING_BRISK');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Running',
    'RUNNING_GENERAL',
    10.5,
    'General outdoor or treadmill running.',
    NULL,
    'Lower Body',
    'Glutes, Hamstrings, Calves, Core',
    'None',
    'INTERMEDIATE',
    'Run with a relaxed upper body, land softly, and keep a steady cadence.',
    'Warm up first and avoid sudden intensity increases if returning from injury.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'RUNNING_GENERAL');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Cycling',
    'CYCLING_MODERATE',
    8.0,
    'Moderate intensity cycling on a bike or stationary bike.',
    NULL,
    'Lower Body',
    'Quadriceps, Glutes, Hamstrings, Calves',
    'Bike',
    'BEGINNER',
    'Set the saddle height correctly, keep a smooth pedal stroke, and maintain controlled breathing.',
    'Check bike setup and avoid locking the knees at the bottom of the pedal stroke.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'CYCLING_MODERATE');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Swimming',
    'SWIMMING_FREESTYLE',
    9.0,
    'Freestyle swimming for full-body conditioning.',
    NULL,
    'Full Body',
    'Back, Shoulders, Core, Legs',
    'Pool',
    'INTERMEDIATE',
    'Keep a long body line, rotate through the torso, and breathe in a controlled rhythm.',
    'Train in supervised water and stop if breathing becomes uncontrolled.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'SWIMMING_FREESTYLE');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Bodyweight Squat',
    'BODYWEIGHT_SQUAT',
    5.0,
    'Bodyweight lower-body strength movement.',
    NULL,
    'Quadriceps',
    'Glutes, Hamstrings, Core',
    'None',
    'BEGINNER',
    'Stand tall, brace your core, lower hips back and down, then drive through the feet to stand.',
    'Keep knees tracking with toes and avoid rounding the lower back.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'BODYWEIGHT_SQUAT');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Push-Up',
    'PUSH_UP',
    6.0,
    'Bodyweight upper-body pushing movement.',
    NULL,
    'Chest',
    'Triceps, Shoulders, Core',
    'None',
    'BEGINNER',
    'Start in a plank, lower the chest under control, then press back up while keeping the body aligned.',
    'Use an incline or knee variation if form breaks down.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'PUSH_UP');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Plank',
    'PLANK',
    3.5,
    'Static core stability exercise.',
    NULL,
    'Core',
    'Shoulders, Glutes',
    'None',
    'BEGINNER',
    'Hold a straight line from head to heels, brace the core, and breathe steadily.',
    'Stop when hips sag or lower back discomfort appears.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'PLANK');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Jump Rope',
    'JUMP_ROPE',
    12.0,
    'High intensity rope skipping for conditioning.',
    NULL,
    'Full Body',
    'Calves, Shoulders, Core',
    'Jump Rope',
    'INTERMEDIATE',
    'Keep jumps low, wrists relaxed, and land softly on the balls of the feet.',
    'Avoid hard surfaces and reduce volume if calves or shins become painful.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'JUMP_ROPE');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Dumbbell Deadlift',
    'DUMBBELL_DEADLIFT',
    6.5,
    'Hip hinge strength movement using dumbbells.',
    NULL,
    'Hamstrings',
    'Glutes, Lower Back, Core',
    'Dumbbells',
    'INTERMEDIATE',
    'Hinge at the hips, keep the weights close, maintain a neutral spine, and stand by driving hips forward.',
    'Start light and stop if lower back position cannot be maintained.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'DUMBBELL_DEADLIFT');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Bench Press',
    'BENCH_PRESS',
    5.5,
    'Upper-body barbell or dumbbell pressing movement.',
    NULL,
    'Chest',
    'Triceps, Shoulders',
    'Bench, Barbell or Dumbbells',
    'INTERMEDIATE',
    'Set shoulder blades, lower the weight under control, then press up without losing upper-back tension.',
    'Use a spotter or safety setup when lifting heavy.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'BENCH_PRESS');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Yoga',
    'YOGA_HATHA',
    3.0,
    'Low intensity yoga session for mobility and recovery.',
    NULL,
    'Full Body',
    'Core, Hips, Shoulders',
    'Mat',
    'BEGINNER',
    'Move slowly between poses, keep breathing steady, and stay within a comfortable range of motion.',
    'Avoid forcing end ranges and modify poses when pain appears.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'YOGA_HATHA');

INSERT INTO exercise_items (
    name, met_code, calories_per_minute, description, icon_url,
    primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, thumbnail_url, video_url, animation_url,
    ai_eligible, active
)
SELECT
    'Rowing Machine',
    'ROWING_MACHINE',
    8.5,
    'Indoor rowing machine cardio exercise.',
    NULL,
    'Full Body',
    'Back, Legs, Core, Arms',
    'Rowing Machine',
    'INTERMEDIATE',
    'Drive with the legs first, swing through the hips, then pull with the arms; reverse the order on recovery.',
    'Keep the back neutral and avoid pulling only with the arms.',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM exercise_items WHERE met_code = 'ROWING_MACHINE');
