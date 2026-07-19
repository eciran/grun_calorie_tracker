UPDATE ai_credit_pricing_policies
SET base_credit_cost = 1,
    included_units = 2,
    units_per_additional_credit = 6,
    context_surcharge = 2,
    max_credit_cost = 10,
    updated_at = CURRENT_TIMESTAMP
WHERE feature = 'AI_NUTRITION_PLAN';

UPDATE ai_credit_pricing_policies
SET base_credit_cost = 1,
    included_units = 30,
    units_per_additional_credit = 90,
    context_surcharge = 0,
    max_credit_cost = 6,
    updated_at = CURRENT_TIMESTAMP
WHERE feature = 'AI_WORKOUT_PLANNER';
