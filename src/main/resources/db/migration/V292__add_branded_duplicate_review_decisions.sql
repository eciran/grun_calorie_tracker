CREATE TABLE food_branded_duplicate_decisions (
    id BIGSERIAL PRIMARY KEY,
    brand_key VARCHAR(300) NOT NULL,
    name_key VARCHAR(500) NOT NULL,
    decision VARCHAR(30) NOT NULL,
    survivor_food_item_id BIGINT NULL,
    candidate_fingerprint VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_branded_duplicate_identity UNIQUE (brand_key, name_key),
    CONSTRAINT fk_food_branded_duplicate_survivor
        FOREIGN KEY (survivor_food_item_id) REFERENCES food_items(id),
    CONSTRAINT chk_food_branded_duplicate_decision
        CHECK (decision IN ('KEEP_SEPARATE', 'BLOCKED', 'SURVIVOR_SELECTED')),
    CONSTRAINT chk_food_branded_duplicate_survivor
        CHECK (
            (decision = 'SURVIVOR_SELECTED' AND survivor_food_item_id IS NOT NULL)
            OR (decision <> 'SURVIVOR_SELECTED' AND survivor_food_item_id IS NULL)
        )
);

CREATE INDEX idx_food_branded_duplicate_decision
    ON food_branded_duplicate_decisions(decision, reviewed_at DESC);

CREATE TABLE food_branded_duplicate_decision_audits (
    id BIGSERIAL PRIMARY KEY,
    brand_key VARCHAR(300) NOT NULL,
    name_key VARCHAR(500) NOT NULL,
    action VARCHAR(20) NOT NULL,
    previous_decision VARCHAR(30) NULL,
    new_decision VARCHAR(30) NULL,
    previous_survivor_food_item_id BIGINT NULL,
    new_survivor_food_item_id BIGINT NULL,
    candidate_fingerprint VARCHAR(64) NULL,
    reason VARCHAR(1000) NOT NULL,
    reviewed_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_food_branded_duplicate_audit_action CHECK (action IN ('SET', 'CLEAR')),
    CONSTRAINT chk_food_branded_duplicate_previous_decision CHECK (
        previous_decision IS NULL OR previous_decision IN ('KEEP_SEPARATE', 'BLOCKED', 'SURVIVOR_SELECTED')
    ),
    CONSTRAINT chk_food_branded_duplicate_new_decision CHECK (
        new_decision IS NULL OR new_decision IN ('KEEP_SEPARATE', 'BLOCKED', 'SURVIVOR_SELECTED')
    )
);

CREATE INDEX idx_food_branded_duplicate_audit_identity
    ON food_branded_duplicate_decision_audits(brand_key, name_key, created_at DESC);
