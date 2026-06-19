CREATE TABLE IF NOT EXISTS product_analytics_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    event_type VARCHAR(80) NOT NULL,
    surface VARCHAR(120),
    market_region VARCHAR(40),
    language VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    target_type VARCHAR(80),
    target_id BIGINT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_analytics_events_user_created
    ON product_analytics_events(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_product_analytics_events_type_created
    ON product_analytics_events(event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_product_analytics_events_region_created
    ON product_analytics_events(market_region, created_at DESC);
