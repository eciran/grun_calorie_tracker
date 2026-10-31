-- Presentation-only product families. Physical products, GTINs and nutrition rows remain independent.
CREATE TABLE food_product_families (
    id BIGSERIAL PRIMARY KEY,
    family_key VARCHAR(180) NOT NULL,
    brand_key VARCHAR(160) NOT NULL,
    variant_key VARCHAR(240) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    representative_food_item_id BIGINT NOT NULL,
    evidence_reason VARCHAR(1000) NOT NULL,
    applied_by VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reverted_by VARCHAR(255),
    reverted_at TIMESTAMP,
    revert_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_food_product_family_key UNIQUE (family_key),
    CONSTRAINT fk_food_product_family_representative
        FOREIGN KEY (representative_food_item_id) REFERENCES food_items(id),
    CONSTRAINT chk_food_product_family_state CHECK (
        (active = TRUE AND reverted_at IS NULL AND reverted_by IS NULL)
        OR (active = FALSE)
    )
);

CREATE UNIQUE INDEX uq_food_product_family_active_representative
    ON food_product_families(representative_food_item_id) WHERE active = TRUE;
CREATE INDEX idx_food_product_family_identity
    ON food_product_families(brand_key, variant_key, active);

CREATE TABLE food_product_family_members (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    food_item_id BIGINT NOT NULL,
    package_quantity NUMERIC(12,3) NOT NULL,
    package_unit VARCHAR(24) NOT NULL,
    package_label VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    default_variant BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_food_product_family_member_product UNIQUE (food_item_id),
    CONSTRAINT uq_food_product_family_member_package
        UNIQUE (family_id, package_quantity, package_unit, food_item_id),
    CONSTRAINT fk_food_product_family_member_family
        FOREIGN KEY (family_id) REFERENCES food_product_families(id) ON DELETE CASCADE,
    CONSTRAINT fk_food_product_family_member_product
        FOREIGN KEY (food_item_id) REFERENCES food_items(id),
    CONSTRAINT chk_food_product_family_member_quantity CHECK (package_quantity > 0),
    CONSTRAINT chk_food_product_family_member_sort CHECK (sort_order >= 0)
);

CREATE INDEX idx_food_product_family_member_family_order
    ON food_product_family_members(family_id, sort_order, package_quantity);

CREATE TRIGGER trg_product_cache_fence
    BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON food_product_families
    FOR EACH STATEMENT EXECUTE FUNCTION public.grun_product_cache_write_fence();
CREATE TRIGGER trg_product_cache_fence
    BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON food_product_family_members
    FOR EACH STATEMENT EXECUTE FUNCTION public.grun_product_cache_write_fence();
