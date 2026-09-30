CREATE TABLE food_branded_duplicate_search_collapses (
    id BIGSERIAL PRIMARY KEY,
    decision_id BIGINT NOT NULL,
    brand_key VARCHAR(300) NOT NULL,
    name_key VARCHAR(500) NOT NULL,
    survivor_food_item_id BIGINT NOT NULL,
    candidate_fingerprint VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    applied_by VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reverted_by VARCHAR(255) NULL,
    reverted_at TIMESTAMP NULL,
    revert_reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_branded_duplicate_search_collapse_decision UNIQUE (decision_id),
    CONSTRAINT fk_food_branded_duplicate_search_collapse_decision
        FOREIGN KEY (decision_id) REFERENCES food_branded_duplicate_decisions(id),
    CONSTRAINT fk_food_branded_duplicate_search_collapse_survivor
        FOREIGN KEY (survivor_food_item_id) REFERENCES food_items(id),
    CONSTRAINT chk_food_branded_duplicate_search_collapse_state CHECK (
        (active = TRUE AND reverted_by IS NULL AND reverted_at IS NULL AND revert_reason IS NULL)
        OR
        (active = FALSE AND reverted_by IS NOT NULL AND reverted_at IS NOT NULL AND revert_reason IS NOT NULL)
    )
);

CREATE INDEX idx_food_branded_duplicate_search_collapse_active
    ON food_branded_duplicate_search_collapses(active, survivor_food_item_id);

CREATE TABLE food_branded_duplicate_search_collapse_members (
    id BIGSERIAL PRIMARY KEY,
    collapse_id BIGINT NOT NULL,
    suppressed_food_item_id BIGINT NOT NULL,
    CONSTRAINT uq_food_branded_duplicate_search_collapse_member
        UNIQUE (collapse_id, suppressed_food_item_id),
    CONSTRAINT fk_food_branded_duplicate_search_collapse_member_collapse
        FOREIGN KEY (collapse_id) REFERENCES food_branded_duplicate_search_collapses(id) ON DELETE CASCADE,
    CONSTRAINT fk_food_branded_duplicate_search_collapse_member_product
        FOREIGN KEY (suppressed_food_item_id) REFERENCES food_items(id)
);

CREATE INDEX idx_food_branded_duplicate_search_collapse_member_product
    ON food_branded_duplicate_search_collapse_members(suppressed_food_item_id, collapse_id);

CREATE TABLE food_branded_duplicate_search_collapse_audits (
    id BIGSERIAL PRIMARY KEY,
    decision_id BIGINT NOT NULL,
    brand_key VARCHAR(300) NOT NULL,
    name_key VARCHAR(500) NOT NULL,
    action VARCHAR(20) NOT NULL,
    survivor_food_item_id BIGINT NOT NULL,
    candidate_fingerprint VARCHAR(64) NOT NULL,
    suppressed_food_item_ids TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_food_branded_duplicate_search_collapse_audit_action
        CHECK (action IN ('APPLY', 'REVERT'))
);

CREATE INDEX idx_food_branded_duplicate_search_collapse_audit_identity
    ON food_branded_duplicate_search_collapse_audits(brand_key, name_key, created_at DESC);
