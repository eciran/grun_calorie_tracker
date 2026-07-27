ALTER TABLE product_analytics_events
    ADD COLUMN IF NOT EXISTS event_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_product_analytics_events_admin_dimensions
    ON product_analytics_events(created_at, event_type, market_region, language);

CREATE INDEX IF NOT EXISTS idx_product_analytics_events_feature_adoption
    ON product_analytics_events(event_type, target_type, created_at);
