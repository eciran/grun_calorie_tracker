UPDATE users
SET market_region = 'UK_IE'
WHERE market_region IN ('IRL', 'UK');

UPDATE food_items
SET market_region = 'UK_IE'
WHERE market_region IN ('IRL', 'UK');

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_market_region;

ALTER TABLE users
    ADD CONSTRAINT chk_users_market_region
        CHECK (market_region IS NULL OR market_region IN ('GLOBAL', 'TR', 'UK_IE', 'EU'));

ALTER TABLE food_items
    DROP CONSTRAINT IF EXISTS chk_food_items_market_region;

ALTER TABLE food_items
    ADD CONSTRAINT chk_food_items_market_region
        CHECK (market_region IS NULL OR market_region IN ('GLOBAL', 'TR', 'UK_IE', 'EU'));
