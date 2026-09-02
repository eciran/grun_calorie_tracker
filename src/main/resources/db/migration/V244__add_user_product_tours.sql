CREATE TABLE IF NOT EXISTS user_product_tours (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tour_key VARCHAR(80) NOT NULL,
    tour_version VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_product_tours_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_product_tours_user_key_version
        UNIQUE (user_id, tour_key, tour_version),
    CONSTRAINT chk_user_product_tours_status
        CHECK (status IN ('COMPLETED', 'SKIPPED', 'DISMISSED'))
);

CREATE INDEX IF NOT EXISTS idx_user_product_tours_user
    ON user_product_tours(user_id);

-- Existing accounts predate this tour. Mark them as skipped so a release does
-- not unexpectedly interrupt returning users; new accounts have no row and
-- receive the tour after onboarding.
INSERT INTO user_product_tours (
    user_id, tour_key, tour_version, status, completed_at, updated_at)
SELECT id, 'dashboard', 'dashboard-tour-v1', 'SKIPPED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users
ON CONFLICT (user_id, tour_key, tour_version) DO NOTHING;
