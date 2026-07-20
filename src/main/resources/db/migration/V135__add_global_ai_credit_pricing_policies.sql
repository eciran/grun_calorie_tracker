CREATE TABLE ai_credit_pricing_policies (
    id BIGSERIAL PRIMARY KEY,
    feature VARCHAR(60) NOT NULL,
    pricing_mode VARCHAR(40) NOT NULL,
    base_credit_cost INTEGER NOT NULL,
    included_units INTEGER NOT NULL,
    units_per_additional_credit INTEGER NOT NULL,
    context_surcharge INTEGER NOT NULL,
    max_credit_cost INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_credit_pricing_policy_feature UNIQUE (feature),
    CONSTRAINT chk_ai_credit_pricing_base CHECK (base_credit_cost BETWEEN 1 AND 50),
    CONSTRAINT chk_ai_credit_pricing_included CHECK (included_units >= 0),
    CONSTRAINT chk_ai_credit_pricing_unit_size CHECK (units_per_additional_credit > 0),
    CONSTRAINT chk_ai_credit_pricing_context CHECK (context_surcharge BETWEEN 0 AND 50),
    CONSTRAINT chk_ai_credit_pricing_max CHECK (max_credit_cost BETWEEN 1 AND 50),
    CONSTRAINT chk_ai_credit_pricing_bounds CHECK (max_credit_cost >= base_credit_cost)
);

INSERT INTO ai_credit_pricing_policies
    (feature, pricing_mode, base_credit_cost, included_units,
     units_per_additional_credit, context_surcharge, max_credit_cost)
VALUES
    ('AI_MEAL_DRAFTS', 'FIXED', 1, 0, 1, 0, 1),
    ('AI_RECIPE_GENERATION', 'FIXED', 1, 0, 1, 0, 1),
    ('AI_MEAL_PREPARATION_GUIDE', 'FIXED', 1, 0, 1, 0, 1),
    ('AI_NUTRITION_PLAN', 'NUTRITION_COMPLEXITY', 1, 4, 8, 1, 8),
    ('AI_WORKOUT_PLANNER', 'WORKOUT_COMPLEXITY', 1, 90, 120, 0, 8),
    ('AI_INSIGHTS', 'FIXED', 1, 0, 1, 0, 1);
