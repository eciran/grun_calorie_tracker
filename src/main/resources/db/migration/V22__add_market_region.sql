ALTER TABLE users
    ADD COLUMN IF NOT EXISTS market_region VARCHAR(16);

ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS market_region VARCHAR(16);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_market_region;

ALTER TABLE users
    ADD CONSTRAINT chk_users_market_region
        CHECK (market_region IS NULL OR market_region IN ('IRL', 'TR', 'UK'));

ALTER TABLE food_items
    DROP CONSTRAINT IF EXISTS chk_food_items_market_region;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_market_region
        CHECK (market_region IS NULL OR market_region IN ('IRL', 'TR', 'UK'));

CREATE INDEX IF NOT EXISTS idx_food_items_market_region_search
    ON food_items (market_region, quality_score DESC, usage_count DESC, name ASC);

CREATE INDEX IF NOT EXISTS idx_users_market_region
    ON users (market_region);
