CREATE TABLE user_analytics_cache_revisions (
    user_id BIGINT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_analytics_cache_revision_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

INSERT INTO user_analytics_cache_revisions (user_id, revision)
SELECT id, 0
FROM users
ON CONFLICT (user_id) DO NOTHING;

