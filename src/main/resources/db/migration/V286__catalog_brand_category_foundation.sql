create table food_brands (
    id bigserial primary key,
    canonical_name varchar(255) not null,
    normalized_key varchar(255) not null,
    manufacturer_name varchar(255),
    country_code varchar(2),
    logo_url varchar(1000),
    status varchar(30) not null default 'ACTIVE',
    merged_into_id bigint references food_brands(id),
    source varchar(40),
    verified boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint chk_food_brands_status check (status in ('ACTIVE', 'INACTIVE', 'MERGED')),
    constraint chk_food_brands_merge check (
        (status = 'MERGED' and merged_into_id is not null and merged_into_id <> id)
        or (status <> 'MERGED' and merged_into_id is null)
    )
);

create unique index uk_food_brands_active_normalized_key
    on food_brands(normalized_key) where status = 'ACTIVE';
create index idx_food_brands_status_name on food_brands(status, canonical_name);

create table food_brand_aliases (
    id bigserial primary key,
    brand_id bigint not null references food_brands(id),
    alias varchar(255) not null,
    normalized_alias varchar(255) not null,
    source varchar(40),
    created_at timestamp not null default current_timestamp,
    created_by varchar(255),
    constraint uk_food_brand_aliases_normalized unique (normalized_alias)
);
create index idx_food_brand_aliases_brand on food_brand_aliases(brand_id);

alter table food_items add column brand_id bigint references food_brands(id);
create index idx_food_items_brand_id on food_items(brand_id);

create table food_categories (
    id bigserial primary key,
    parent_id bigint references food_categories(id),
    slug varchar(160) not null unique,
    name_en varchar(255) not null,
    name_tr varchar(255) not null,
    description_en varchar(1000),
    description_tr varchar(1000),
    icon_key varchar(120),
    image_url varchar(1000),
    sort_order integer not null default 0,
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint chk_food_categories_not_self_parent check (parent_id is null or parent_id <> id)
);
create index idx_food_categories_parent_active_order
    on food_categories(parent_id, active, sort_order, id);

create table food_item_categories (
    food_item_id bigint not null references food_items(id) on delete cascade,
    category_id bigint not null references food_categories(id),
    primary_category boolean not null default false,
    assignment_source varchar(40) not null,
    confidence_score integer,
    reviewed boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    primary key (food_item_id, category_id),
    constraint chk_food_item_categories_source check (
        assignment_source in ('ADMIN', 'SOURCE_MAPPING', 'IMPORT', 'AI_SUGGESTION')
    ),
    constraint chk_food_item_categories_confidence check (
        confidence_score is null or confidence_score between 0 and 100
    )
);
create unique index uk_food_item_categories_one_primary
    on food_item_categories(food_item_id) where primary_category;
create index idx_food_item_categories_category on food_item_categories(category_id, food_item_id);

create table food_category_source_mappings (
    id bigserial primary key,
    data_source varchar(40) not null,
    source_tag varchar(180) not null,
    normalized_source_tag varchar(180) not null,
    market_region varchar(30),
    category_id bigint not null references food_categories(id),
    status varchar(30) not null default 'REVIEW_REQUIRED',
    confidence_score integer,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint chk_food_category_mapping_status check (
        status in ('ACTIVE', 'IGNORED', 'REVIEW_REQUIRED')
    ),
    constraint chk_food_category_mapping_confidence check (
        confidence_score is null or confidence_score between 0 and 100
    )
);
create unique index uk_food_category_mapping_scope
    on food_category_source_mappings(
        data_source,
        normalized_source_tag,
        coalesce(market_region, '')
    );
create index idx_food_category_mapping_lookup
    on food_category_source_mappings(data_source, normalized_source_tag, market_region, status);
