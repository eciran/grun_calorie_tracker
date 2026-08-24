INSERT INTO exercise_items
(name, met_code, calories_per_minute, description, primary_muscle_group, secondary_muscle_groups,
 equipment, difficulty, instructions, safety_notes, default_measurement_type, allowed_measurement_types,
 ai_eligible, active, technique_review_status, source_name, source_url, license_name, source_last_refreshed_at)
SELECT v.name, v.code, v.calories, v.description, v.primary_muscle, v.secondary_muscles,
       v.equipment, v.difficulty, v.instructions, v.safety, v.default_measurement, v.allowed_measurements,
       TRUE, TRUE, 'APPROVED', 'GRUN curated exercise catalog',
       'https://gruncalorietracker.com/exercise-methodology', 'GRUN editorial', CURRENT_TIMESTAMP
FROM (VALUES
 ('Front Squat','FRONT_SQUAT',6.8,'Front-loaded barbell squat.','Quadriceps','Glutes, Core, Upper Back','Barbell','INTERMEDIATE','Keep elbows high, brace, squat with the whole foot grounded, then stand under control.','Use safety pins and reduce the load if the torso collapses.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Sumo Deadlift','SUMO_DEADLIFT',7.2,'Wide-stance barbell pull from the floor.','Glutes','Hamstrings, Quadriceps, Back, Core','Barbell','INTERMEDIATE','Set a comfortable wide stance, brace, push the floor away, and keep the bar close.','Do not force an excessively wide stance or continue through hip pain.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Walking Lunge','WALKING_LUNGE',6.0,'Alternating forward lunges performed while travelling.','Quadriceps','Glutes, Hamstrings, Core','Bodyweight or Dumbbells','BEGINNER','Step to a stable stance, lower both knees with control, and drive through the lead foot.','Use shorter steps or bodyweight if balance or knee control is limited.','SETS_REPS','SETS_REPS,REPS,WEIGHT_REPS,DISTANCE'),
 ('Reverse Lunge','REVERSE_LUNGE',5.5,'Alternating backward lunge.','Quadriceps','Glutes, Hamstrings, Core','Bodyweight or Dumbbells','BEGINNER','Step backward, lower under control, and return by driving through the front foot.','Keep the front knee tracking steadily and use support if needed.','SETS_REPS','SETS_REPS,REPS,WEIGHT_REPS'),
 ('Hack Squat','HACK_SQUAT',5.8,'Machine-supported squat pattern.','Quadriceps','Glutes, Hamstrings','Hack Squat Machine','BEGINNER','Set the feet securely, descend to a controlled depth, and press through the platform.','Do not lock the knees forcefully or allow the hips to lift from the pad.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Seated Calf Raise','SEATED_CALF_RAISE',3.8,'Seated loaded ankle plantar flexion.','Calves','Feet, Ankles','Seated Calf Machine','BEGINNER','Lower the heels under control, rise through the balls of the feet, and pause at the top.','Avoid bouncing and use a pain-free ankle range.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Machine Chest Press','MACHINE_CHEST_PRESS',5.0,'Machine-supported horizontal chest press.','Chest','Triceps, Shoulders','Chest Press Machine','BEGINNER','Set the handles near mid-chest, keep shoulders supported, and press smoothly.','Adjust the seat to avoid painful shoulder depth.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Incline Barbell Bench Press','INCLINE_BARBELL_BENCH_PRESS',5.8,'Incline chest press using a barbell.','Chest','Shoulders, Triceps','Barbell, Incline Bench','INTERMEDIATE','Use a moderate incline, lower the bar with control, and press over the upper chest.','Use safety arms or a spotter and avoid excessive shoulder flare.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Pec Deck Fly','PEC_DECK_FLY',4.3,'Machine chest fly.','Chest','Shoulders','Pec Deck Machine','BEGINNER','Keep the torso supported and bring the pads together in a controlled arc.','Set a range that does not overstretch the shoulders.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Chest-Supported Row','CHEST_SUPPORTED_ROW',4.8,'Horizontal row with the torso supported.','Back','Biceps, Rear Shoulders','Dumbbells or Row Machine, Bench','BEGINNER','Keep the chest supported, pull elbows toward the hips, and lower fully.','Avoid shrugging or lifting the chest from the support.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('T-Bar Row','T_BAR_ROW',5.8,'Loaded horizontal pulling movement.','Back','Biceps, Rear Shoulders, Core','T-Bar or Landmine','INTERMEDIATE','Brace the trunk, pull toward the lower ribs, and lower without losing position.','Reduce the load if the lower back cannot remain stable.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Straight-Arm Pulldown','STRAIGHT_ARM_PULLDOWN',4.2,'Cable shoulder-extension exercise for the lats.','Back','Triceps, Core','Cable Machine','BEGINNER','Keep a soft elbow bend and pull the bar toward the thighs without swinging.','Use a light load and avoid forcing shoulder range.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Machine Shoulder Press','MACHINE_SHOULDER_PRESS',4.8,'Machine-supported overhead press.','Shoulders','Triceps','Shoulder Press Machine','BEGINNER','Set the seat so handles begin near shoulder height and press without shrugging.','Use a pain-free range and avoid excessive back extension.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Cable Lateral Raise','CABLE_LATERAL_RAISE',3.8,'Cable shoulder abduction exercise.','Shoulders','Upper Back','Cable Machine','BEGINNER','Raise the arm with a soft elbow to a comfortable height and lower slowly.','Avoid leaning, swinging, or shrugging.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Barbell Curl','BARBELL_CURL',4.2,'Standing elbow flexion using a barbell.','Biceps','Forearms','Barbell','BEGINNER','Keep elbows close and curl without moving the torso.','Use a comfortable grip and reduce load if wrists hurt.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Preacher Curl','PREACHER_CURL',3.8,'Supported elbow-flexion exercise.','Biceps','Forearms','Preacher Bench, EZ Bar or Dumbbell','BEGINNER','Keep the upper arms supported and curl through a controlled range.','Avoid forceful elbow lockout at the bottom.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Overhead Triceps Extension','OVERHEAD_TRICEPS_EXTENSION',4.0,'Overhead elbow-extension exercise.','Triceps','Shoulders, Core','Dumbbell or Cable','BEGINNER','Keep elbows controlled and extend the load without arching the back.','Use a comfortable shoulder position and a manageable load.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Lying Triceps Extension','LYING_TRICEPS_EXTENSION',4.0,'Lying elbow-extension exercise, commonly called skull crusher.','Triceps','Forearms','EZ Bar or Dumbbells, Bench','INTERMEDIATE','Keep upper arms stable, lower toward a safe point, and extend under control.','Use collars and avoid lowering the load toward the face when fatigued.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Cable Crunch','CABLE_CRUNCH',4.0,'Kneeling loaded trunk-flexion exercise.','Core','Hip Flexors','Cable Machine','BEGINNER','Brace and flex the ribs toward the pelvis without pulling with the arms.','Use a controlled load and avoid neck pulling.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Russian Twist','RUSSIAN_TWIST',4.5,'Seated controlled trunk-rotation exercise.','Core','Hip Flexors, Obliques','Mat, Weight optional','BEGINNER','Sit tall, brace, and rotate the ribcage through a comfortable range.','Keep the movement controlled and stop if the lower back hurts.','REPS','REPS,SETS_REPS,DURATION'),
 ('Lying Leg Raise','LYING_LEG_RAISE',4.0,'Supine leg-raise core exercise.','Core','Hip Flexors','Mat','BEGINNER','Brace the trunk, raise and lower the legs without arching the lower back.','Bend the knees or shorten the range if the back lifts.','REPS','REPS,SETS_REPS,DURATION'),
 ('Hollow Body Hold','HOLLOW_BODY_HOLD',4.0,'Supine isometric trunk hold.','Core','Hip Flexors','Mat','INTERMEDIATE','Press the lower back gently down and hold a compact braced position.','Tuck the knees if the lower back cannot stay controlled.','DURATION','DURATION'),
 ('Back Extension','BACK_EXTENSION',4.2,'Supported hip and spinal extension exercise.','Back','Glutes, Hamstrings','Back Extension Bench','BEGINNER','Hinge through the hips and return to a straight body line under control.','Do not hyperextend or continue through back pain.','SETS_REPS','SETS_REPS,REPS,WEIGHT_REPS'),
 ('Hip Abduction Machine','HIP_ABDUCTION_MACHINE',3.5,'Machine hip-abduction exercise.','Glutes','Outer Hips','Hip Abduction Machine','BEGINNER','Keep the pelvis supported and open the knees under control.','Use a comfortable range without bouncing.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Hip Adduction Machine','HIP_ADDUCTION_MACHINE',3.5,'Machine hip-adduction exercise.','Adductors','Inner Thighs','Hip Adduction Machine','BEGINNER','Keep the pelvis supported and bring the knees together smoothly.','Use a pain-free range and avoid forcing the stretch.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Landmine Press','LANDMINE_PRESS',5.0,'Angled pressing exercise using a landmine.','Shoulders','Chest, Triceps, Core','Landmine, Barbell','BEGINNER','Brace and press the bar upward and forward without rotating the trunk.','Use a secure landmine attachment and avoid excessive back extension.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS'),
 ('Sled Push','SLED_PUSH',8.0,'Loaded pushing exercise performed over distance.','Full Body','Quadriceps, Glutes, Calves, Shoulders','Sled','INTERMEDIATE','Brace, drive through short powerful steps, and keep the path clear.','Use a manageable load and stop if posture or footing becomes unstable.','DISTANCE','DISTANCE,DURATION,MIXED')
) AS v(name,code,calories,description,primary_muscle,secondary_muscles,equipment,difficulty,instructions,safety,default_measurement,allowed_measurements)
WHERE NOT EXISTS (SELECT 1 FROM exercise_items e WHERE e.met_code = v.code);

WITH aliases(code, alias, normalized_alias, language) AS (VALUES
 ('FRONT_SQUAT','Ön Squat','on squat','tr'),
 ('SUMO_DEADLIFT','Sumo Deadlift','sumo deadlift','tr'),
 ('WALKING_LUNGE','Yürüyüş Lunge','yuruyus lunge','tr'),
 ('REVERSE_LUNGE','Geri Lunge','geri lunge','tr'),
 ('MACHINE_CHEST_PRESS','Makine Göğüs Press','makine gogus press','tr'),
 ('CHEST_SUPPORTED_ROW','Göğüs Destekli Row','gogus destekli row','tr'),
 ('BARBELL_CURL','Barbell Biceps Curl','barbell biceps curl','tr'),
 ('OVERHEAD_TRICEPS_EXTENSION','Baş Üstü Triceps Extension','bas ustu triceps extension','tr'),
 ('LYING_TRICEPS_EXTENSION','Skull Crusher','skull crusher','und'),
 ('RUSSIAN_TWIST','Rus Dönüşü','rus donusu','tr'),
 ('LYING_LEG_RAISE','Yatarak Bacak Kaldırma','yatarak bacak kaldirma','tr'),
 ('BACK_EXTENSION','Bel Extension','bel extension','tr')
)
INSERT INTO exercise_item_aliases(exercise_item_id, alias, normalized_alias, language, alias_type, active)
SELECT e.id, a.alias, a.normalized_alias, a.language, 'LOCALIZED', TRUE
FROM aliases a JOIN exercise_items e ON e.met_code = a.code
ON CONFLICT (normalized_alias, language) DO NOTHING;
