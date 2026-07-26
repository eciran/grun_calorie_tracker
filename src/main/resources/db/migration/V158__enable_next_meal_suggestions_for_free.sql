INSERT INTO subscription_plan_features (
    plan_type,
    feature,
    enabled,
    effective_from,
    updated_at
)
VALUES ('FREE', 'NEXT_MEAL_SUGGESTIONS', TRUE, CURRENT_DATE, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type, feature)
DO UPDATE SET
    enabled = TRUE,
    effective_from = CURRENT_DATE,
    updated_at = CURRENT_TIMESTAMP;