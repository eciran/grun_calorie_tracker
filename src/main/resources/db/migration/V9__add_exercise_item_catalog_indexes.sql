CREATE INDEX IF NOT EXISTS idx_exercise_items_active_name
    ON exercise_items (active, name);

CREATE INDEX IF NOT EXISTS idx_exercise_items_difficulty
    ON exercise_items (difficulty);

CREATE INDEX IF NOT EXISTS idx_exercise_items_primary_muscle_group
    ON exercise_items (primary_muscle_group);

CREATE INDEX IF NOT EXISTS idx_exercise_items_equipment
    ON exercise_items (equipment);
