CREATE TABLE ai_credit_debits (
    request_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_amount INTEGER NOT NULL CHECK (plan_amount >= 0),
    addon_amount INTEGER NOT NULL CHECK (addon_amount >= 0),
    allocation_key VARCHAR(64),
    plan_start DATE,
    plan_end DATE,
    addon_expires DATE,
    refunded_amount INTEGER NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    refunded BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_credit_debits_user ON ai_credit_debits(user_id);
