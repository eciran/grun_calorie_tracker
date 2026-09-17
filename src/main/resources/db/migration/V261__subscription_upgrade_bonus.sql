ALTER TABLE subscriptions
    ADD COLUMN ai_upgrade_bonus INTEGER NOT NULL DEFAULT 0
    CHECK (ai_upgrade_bonus >= 0);

ALTER TABLE subscription_credit_allocations
    ADD COLUMN upgrade_bonus INTEGER NOT NULL DEFAULT 0
    CHECK (upgrade_bonus >= 0);
