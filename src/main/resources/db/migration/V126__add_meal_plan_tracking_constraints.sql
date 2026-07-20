WITH ranked_active_plans AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY updated_at DESC NULLS LAST, id DESC
           ) AS row_number
    FROM meal_plans
    WHERE status = 'ACTIVE'
)
UPDATE meal_plans plan
SET status = 'DRAFT'
FROM ranked_active_plans ranked
WHERE plan.id = ranked.id
  AND ranked.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_meal_plans_one_active_per_user
    ON meal_plans(user_id)
    WHERE status = 'ACTIVE';

DELETE FROM meal_plan_item_consumptions older
USING meal_plan_item_consumptions newer
WHERE older.user_id = newer.user_id
  AND older.meal_plan_item_id = newer.meal_plan_item_id
  AND older.id < newer.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_meal_plan_consumption_user_item
    ON meal_plan_item_consumptions(user_id, meal_plan_item_id);

CREATE INDEX IF NOT EXISTS idx_meal_plans_active_date_lookup
    ON meal_plans(user_id, status, start_date, end_date);
