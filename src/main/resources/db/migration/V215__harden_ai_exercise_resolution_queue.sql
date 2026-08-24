UPDATE unmatched_ai_exercises
SET status = 'OPEN'
WHERE status NOT IN ('OPEN', 'RESOLVED', 'DISMISSED');

UPDATE unmatched_ai_exercises
SET occurrence_count = 1
WHERE occurrence_count < 1;

ALTER TABLE unmatched_ai_exercises
    ADD CONSTRAINT ck_unmatched_ai_exercise_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    ADD CONSTRAINT ck_unmatched_ai_exercise_occurrence_count
        CHECK (occurrence_count > 0),
    ADD CONSTRAINT ck_unmatched_ai_exercise_resolution_target
        CHECK ((status = 'RESOLVED' AND resolved_exercise_item_id IS NOT NULL)
            OR (status <> 'RESOLVED' AND resolved_exercise_item_id IS NULL));

CREATE INDEX IF NOT EXISTS idx_unmatched_ai_exercise_resolved_item
    ON unmatched_ai_exercises(resolved_exercise_item_id)
    WHERE resolved_exercise_item_id IS NOT NULL;
