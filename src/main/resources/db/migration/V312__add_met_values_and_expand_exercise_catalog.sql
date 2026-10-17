ALTER TABLE exercise_items
    ADD COLUMN IF NOT EXISTS met_value DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS intensity_level VARCHAR(24),
    ADD COLUMN IF NOT EXISTS compendium_code VARCHAR(16);

-- Existing catalog values were historically stored as kcal/min, but were authored as
-- MET-like intensity values. Preserve calories_per_minute for old clients and make the
-- intended basis explicit for weight-aware server calculations.
UPDATE exercise_items
SET met_value = calories_per_minute
WHERE met_value IS NULL
  AND calories_per_minute > 0;

UPDATE exercise_items
SET intensity_level = CASE
    WHEN met_value < 3.0 THEN 'LIGHT'
    WHEN met_value < 6.0 THEN 'MODERATE'
    ELSE 'VIGOROUS'
END
WHERE intensity_level IS NULL
  AND met_value IS NOT NULL;

WITH catalog(name, code, met, intensity, compendium, description, primary_muscle,
             secondary_muscles, equipment, difficulty, instructions, safety,
             default_measurement, allowed_measurements, movement_pattern) AS (VALUES
    -- Balance, accessibility and low-impact choices
    ('Single-Leg Stand','SINGLE_LEG_STAND',2.3,'LIGHT',NULL,'Static single-leg balance practice.','Lower Body','Core, Ankles','Support optional','BEGINNER','Stand tall near a stable support and lift one foot only as high as control allows.','Keep a hand near support and stop if dizzy or unsteady.','DURATION','DURATION','BALANCE'),
    ('Tandem Stance','TANDEM_STANCE',2.3,'LIGHT',NULL,'Heel-to-toe static balance practice.','Lower Body','Core, Ankles','Support optional','BEGINNER','Place one foot directly in front of the other and hold a tall, steady posture.','Practice beside a wall or rail until balance is reliable.','DURATION','DURATION','BALANCE'),
    ('Heel-to-Toe Walk','HEEL_TO_TOE_WALK',2.5,'LIGHT',NULL,'Controlled tandem walking drill.','Lower Body','Core, Ankles','Support optional','BEGINNER','Walk slowly by placing the heel directly in front of the opposite toes.','Use a clear level path and stable support when needed.','DISTANCE','DURATION,DISTANCE,MIXED','BALANCE'),
    ('Sit-to-Stand','SIT_TO_STAND',2.8,'LIGHT','02340','Repeated standing from a chair without momentum.','Quadriceps','Glutes, Core','Chair','BEGINNER','Place feet under the knees, lean slightly forward, stand tall, then sit with control.','Use a stable chair and arm support when required.','SETS_REPS','SETS_REPS,REPS,DURATION','SQUAT_LUNGE'),
    ('Chair Squat','CHAIR_SQUAT',3.0,'MODERATE','02056','Supported squat using a chair as a depth target.','Quadriceps','Glutes, Hamstrings, Core','Chair','BEGINNER','Reach the hips toward a stable chair, touch lightly, and stand through the whole foot.','Do not drop onto the chair; use support if balance is limited.','SETS_REPS','SETS_REPS,REPS,DURATION','SQUAT_LUNGE'),
    ('Wall Push-Up','WALL_PUSH_UP',3.0,'MODERATE','02056','Standing push-up performed against a wall.','Chest','Triceps, Shoulders, Core','Wall','BEGINNER','Keep a straight body line, lower toward the wall, and press away under control.','Use a non-slip surface and keep elbows in a comfortable path.','SETS_REPS','SETS_REPS,REPS,DURATION','PUSH'),
    ('Supported Step-Up','SUPPORTED_STEP_UP',4.0,'MODERATE','02344','Low step-up performed with optional hand support.','Quadriceps','Glutes, Calves, Core','Low Step, Support optional','BEGINNER','Place the whole foot on a low step, stand through the lead leg, and descend slowly.','Use a stable step and rail; reduce height if knee control is limited.','SETS_REPS','SETS_REPS,REPS,DURATION','SQUAT_LUNGE'),
    ('Seated March','SEATED_MARCH',2.3,'LIGHT',NULL,'Alternating seated hip-flexion drill.','Hip Flexors','Core, Quadriceps','Chair','BEGINNER','Sit tall and lift alternate knees without leaning back.','Use a stable chair and a pain-free range.','DURATION','DURATION,REPS,SETS_REPS','CARDIO'),
    ('Water Aerobics','WATER_AEROBICS',5.3,'MODERATE','02120','Low-impact aerobic exercise performed in water.','Full Body','Legs, Core, Shoulders','Pool','BEGINNER','Move continuously through comfortable water-based patterns at a sustainable effort.','Use supervised water and follow pool-safety guidance.','DURATION','DURATION','CARDIO'),
    ('Hand Cycling','HAND_CYCLING',3.5,'MODERATE','02118','Upper-body cycling using a hand cycle or arm ergometer.','Upper Body','Shoulders, Arms, Core','Hand Cycle or Arm Ergometer','BEGINNER','Set the handles comfortably and turn with a smooth controlled cadence.','Adjust the setup to avoid shoulder strain and increase resistance gradually.','DURATION','DURATION,DISTANCE,MIXED','CARDIO'),
    ('Wheelchair Cardio','WHEELCHAIR_CARDIO',4.0,'MODERATE',NULL,'Continuous wheelchair propulsion for aerobic conditioning.','Upper Body','Shoulders, Arms, Core','Wheelchair','BEGINNER','Use smooth symmetrical pushes and maintain a sustainable pace.','Choose a safe route, inspect equipment, and avoid shoulder overuse.','DURATION','DURATION,DISTANCE,MIXED','CARDIO'),

    -- Popular sports and health-platform coverage
    ('Volleyball','VOLLEYBALL_GENERAL',4.0,'MODERATE',NULL,'Recreational volleyball match or practice.','Full Body','Legs, Shoulders, Core','Volleyball','BEGINNER','Use balanced footwork and controlled jumping and landing mechanics.','Warm up shoulders, knees, and ankles before play.','DURATION','DURATION','SPORT'),
    ('Table Tennis','TABLE_TENNIS_GENERAL',4.0,'MODERATE',NULL,'Table tennis match or practice.','Full Body','Legs, Core, Shoulders','Table Tennis Paddle','BEGINNER','Stay balanced, use short controlled steps, and rotate through the trunk.','Keep the playing area clear and reduce volume if the elbow or wrist hurts.','DURATION','DURATION','SPORT'),
    ('Golf Walking','GOLF_WALKING',4.3,'MODERATE','15265','Golf played while walking and carrying clubs.','Full Body','Core, Shoulders, Legs','Golf Clubs','BEGINNER','Walk at a comfortable pace and use controlled swings.','Warm up the trunk and shoulders and use sun and hydration precautions.','DURATION','DURATION,DISTANCE,MIXED','SPORT'),
    ('Rock Climbing','ROCK_CLIMBING',8.0,'VIGOROUS','15533','Indoor or outdoor rock climbing session.','Full Body','Grip, Back, Core, Legs','Climbing Equipment','INTERMEDIATE','Climb with deliberate foot placement and maintain three points of contact when practical.','Use certified equipment, correct belay practice, and qualified supervision.','DURATION','DURATION','SPORT'),
    ('Martial Arts','MARTIAL_ARTS_GENERAL',5.3,'MODERATE','15425','General martial-arts technique practice at a moderate pace.','Full Body','Legs, Core, Shoulders','Discipline specific','INTERMEDIATE','Practice controlled technique, stance, and breathing within the rules of the discipline.','Use qualified coaching and appropriate protective equipment.','DURATION','DURATION,REPS','SPORT'),
    ('Paddling','PADDLING_GENERAL',5.0,'MODERATE',NULL,'Kayak, canoe, or similar recreational paddling.','Upper Body','Back, Shoulders, Core','Paddle Craft','BEGINNER','Rotate through the torso and use smooth strokes on both sides.','Wear an approved flotation device and follow local water conditions.','DURATION','DURATION,DISTANCE,MIXED','SPORT'),
    ('Calisthenics','CALISTHENICS_GENERAL',3.8,'MODERATE','02022','Moderate bodyweight exercise session.','Full Body','Chest, Back, Legs, Core','None','BEGINNER','Use controlled bodyweight movements and preserve technique across the session.','Choose regressions when alignment or joint comfort deteriorates.','DURATION','DURATION,SETS_REPS,REPS','FULL_BODY'),
    ('Step Training','STEP_TRAINING',5.5,'MODERATE','02001','Aerobic training using a low step platform.','Lower Body','Glutes, Calves, Core','Step Platform','BEGINNER','Place the whole foot on the step and maintain a controlled rhythm.','Use a stable platform and lower the height if balance or knee comfort is limited.','DURATION','DURATION','CARDIO'),
    ('Open-Water Swimming','SWIMMING_OPEN_WATER',6.0,'VIGOROUS',NULL,'Continuous swimming in open water.','Full Body','Back, Shoulders, Core, Legs','Open Water Safety Equipment','INTERMEDIATE','Use a sustainable stroke and sight regularly while maintaining controlled breathing.','Never swim alone; use appropriate visibility and flotation equipment.','DISTANCE','DURATION,DISTANCE,MIXED','CARDIO'),

    -- Useful strength regressions and missing movement options
    ('Single-Leg Romanian Deadlift','SINGLE_LEG_ROMANIAN_DEADLIFT',5.0,'MODERATE','02052','Single-leg hip hinge using bodyweight or external load.','Hamstrings','Glutes, Core, Ankles','Bodyweight or Dumbbells','INTERMEDIATE','Hinge at the hip while the free leg reaches back and keep the pelvis square.','Use support or a shorter range until balance and hip control are reliable.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS,REPS','HINGE'),
    ('Nordic Hamstring Curl','NORDIC_HAMSTRING_CURL',5.0,'MODERATE','02056','Eccentric bodyweight knee-flexion exercise.','Hamstrings','Glutes, Core','Anchor or Partner','ADVANCED','Keep hips extended and lower the body slowly while resisting with the hamstrings.','Start with assisted partial range and avoid if acute hamstring or knee pain is present.','SETS_REPS','SETS_REPS,REPS','HINGE'),
    ('Band External Rotation','BAND_EXTERNAL_ROTATION',3.5,'MODERATE','02054','Resistance-band shoulder external rotation.','Rotator Cuff','Rear Shoulders, Upper Back','Resistance Band','BEGINNER','Keep the elbow supported near the ribs and rotate outward without moving the torso.','Use light resistance and a pain-free range.','SETS_REPS','SETS_REPS,REPS','PULL'),
    ('Band Reverse Fly','BAND_REVERSE_FLY',3.5,'MODERATE','02054','Resistance-band horizontal shoulder abduction.','Upper Back','Rear Shoulders, Rotator Cuff','Resistance Band','BEGINNER','Keep ribs controlled and pull the band apart without shrugging.','Use light resistance and stop if shoulder pain occurs.','SETS_REPS','SETS_REPS,REPS','PULL'),
    ('Push Press','PUSH_PRESS',6.0,'VIGOROUS','02050','Overhead press assisted by a shallow leg drive.','Shoulders','Triceps, Legs, Core','Barbell or Dumbbells','ADVANCED','Dip vertically, drive through the legs, and finish with the load stacked overhead.','Learn the strict press first and use safety equipment for heavy loads.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS','PUSH'),
    ('Assisted Dip','ASSISTED_DIP',4.5,'MODERATE','02056','Dip performed with machine or band assistance.','Triceps','Chest, Shoulders','Assisted Dip Machine or Band','BEGINNER','Lower through a comfortable range with controlled shoulders, then press to support.','Use enough assistance to avoid shoulder pinching or loss of control.','SETS_REPS','SETS_REPS,REPS,WEIGHT_REPS','PUSH'),
    ('Knee Push-Up','KNEE_PUSH_UP',3.8,'MODERATE','02056','Push-up regression performed from the knees.','Chest','Triceps, Shoulders, Core','Mat','BEGINNER','Maintain a straight line from knees to head, lower under control, and press up.','Use an incline variation if trunk or shoulder control is limited.','SETS_REPS','SETS_REPS,REPS,DURATION','PUSH'),
    ('Box Squat','BOX_SQUAT',5.0,'MODERATE','02052','Squat performed to a box or bench depth target.','Quadriceps','Glutes, Hamstrings, Core','Box or Bench, Weight optional','BEGINNER','Brace, sit back to a controlled box touch, and stand through the whole foot.','Use a stable box and do not relax or rock at the bottom.','WEIGHT_REPS','WEIGHT_REPS,SETS_REPS,REPS','SQUAT_LUNGE')
)
INSERT INTO exercise_items (
    name, met_code, met_value, intensity_level, compendium_code, calories_per_minute,
    description, primary_muscle_group, secondary_muscle_groups, equipment, difficulty,
    instructions, safety_notes, default_measurement_type, allowed_measurement_types,
    movement_pattern, ai_eligible, active, technique_review_status, source_name, source_url,
    source_last_refreshed_at
)
SELECT name, code, met, intensity, compendium, met, description, primary_muscle,
       secondary_muscles, equipment, difficulty, instructions, safety, default_measurement,
       allowed_measurements, movement_pattern, TRUE, TRUE, 'APPROVED',
       'GRUN curated catalog; MET aligned with 2024 Adult Compendium',
       'https://pacompendium.com/adult-compendium/', CURRENT_TIMESTAMP
FROM catalog c
WHERE NOT EXISTS (SELECT 1 FROM exercise_items e WHERE e.met_code = c.code);

WITH aliases(code, alias, normalized_alias, language) AS (VALUES
    ('SINGLE_LEG_STAND','Tek Ayak Duruş','tek ayak durus','tr'),
    ('SIT_TO_STAND','Otur Kalk','otur kalk','tr'),
    ('CHAIR_SQUAT','Sandalyeye Squat','sandalyeye squat','tr'),
    ('WALL_PUSH_UP','Duvar Şınavı','duvar sinavi','tr'),
    ('WATER_AEROBICS','Su Aerobiği','su aerobigi','tr'),
    ('HAND_CYCLING','El Bisikleti','el bisikleti','tr'),
    ('WHEELCHAIR_CARDIO','Tekerlekli Sandalye Kardiyo','tekerlekli sandalye kardiyo','tr'),
    ('TABLE_TENNIS_GENERAL','Masa Tenisi','masa tenisi','tr'),
    ('ROCK_CLIMBING','Kaya Tırmanışı','kaya tirmanisi','tr'),
    ('PADDLING_GENERAL','Kano','kano','tr'),
    ('SWIMMING_OPEN_WATER','Açık Su Yüzme','acik su yuzme','tr'),
    ('SINGLE_LEG_ROMANIAN_DEADLIFT','Tek Bacak RDL','tek bacak rdl','tr'),
    ('NORDIC_HAMSTRING_CURL','Nordic Curl','nordic curl','und'),
    ('KNEE_PUSH_UP','Diz Üstü Şınav','diz ustu sinav','tr'),
    ('BOX_SQUAT','Kutu Squat','kutu squat','tr')
)
INSERT INTO exercise_item_aliases(exercise_item_id, alias, normalized_alias, language, alias_type, active)
SELECT e.id, a.alias, a.normalized_alias, a.language, 'LOCALIZED', TRUE
FROM aliases a JOIN exercise_items e ON e.met_code = a.code
ON CONFLICT (normalized_alias, language) DO NOTHING;

WITH mappings(provider, provider_activity_type, normalized_type, met_code) AS (VALUES
    ('APPLE_HEALTH','HKWorkoutActivityTypeVolleyball','VOLLEYBALL','VOLLEYBALL_GENERAL'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeTableTennis','TABLE_TENNIS','TABLE_TENNIS_GENERAL'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeGolf','GOLF','GOLF_WALKING'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeClimbing','CLIMBING','ROCK_CLIMBING'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeMartialArts','MARTIAL_ARTS','MARTIAL_ARTS_GENERAL'),
    ('APPLE_HEALTH','HKWorkoutActivityTypePaddleSports','PADDLE_SPORTS','PADDLING_GENERAL'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeStepTraining','STEP_TRAINING','STEP_TRAINING'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeHandCycling','HAND_CYCLING','HAND_CYCLING'),
    ('APPLE_HEALTH','HKWorkoutActivityTypeWheelchairWalkPace','WHEELCHAIR_WALK_PACE','WHEELCHAIR_CARDIO'),
    ('HEALTH_CONNECT','VOLLEYBALL','VOLLEYBALL','VOLLEYBALL_GENERAL'),
    ('HEALTH_CONNECT','TABLE_TENNIS','TABLE_TENNIS','TABLE_TENNIS_GENERAL'),
    ('HEALTH_CONNECT','GOLF','GOLF','GOLF_WALKING'),
    ('HEALTH_CONNECT','ROCK_CLIMBING','ROCK_CLIMBING','ROCK_CLIMBING'),
    ('HEALTH_CONNECT','MARTIAL_ARTS','MARTIAL_ARTS','MARTIAL_ARTS_GENERAL'),
    ('HEALTH_CONNECT','PADDLING','PADDLING','PADDLING_GENERAL'),
    ('HEALTH_CONNECT','CALISTHENICS','CALISTHENICS','CALISTHENICS_GENERAL'),
    ('HEALTH_CONNECT','SWIMMING_OPEN_WATER','SWIMMING_OPEN_WATER','SWIMMING_OPEN_WATER'),
    ('HEALTH_CONNECT','WHEELCHAIR','WHEELCHAIR','WHEELCHAIR_CARDIO')
)
INSERT INTO exercise_provider_activity_mappings(
    provider, provider_activity_type, normalized_provider_activity_type, exercise_item_id, active
)
SELECT m.provider, m.provider_activity_type, m.normalized_type, e.id, TRUE
FROM mappings m JOIN exercise_items e ON e.met_code = m.met_code
ON CONFLICT (provider, normalized_provider_activity_type) DO NOTHING;
