-- Fast-food is a discovery facet, not the product's nutritional identity.
-- Keep each product's existing primary category and attach this as a secondary category.

insert into food_categories(
    parent_id, slug, name_en, name_tr, description_en, description_tr, icon_key,
    sort_order, active, created_by, updated_by
)
select parent.id, 'fast-food-takeaway', 'Fast Food & Takeaway', 'Fast Food ve Paket Servis',
       'Restaurant and takeaway products from reviewed fast-food brands.',
       'Incelenmis fast-food markalarina ait restoran ve paket servis urunleri.',
       'hamburger', 50, true, 'migration-v323', 'migration-v323'
from food_categories parent
where parent.slug = 'prepared-meals'
on conflict (slug) do update
set parent_id = excluded.parent_id,
    name_en = excluded.name_en,
    name_tr = excluded.name_tr,
    description_en = excluded.description_en,
    description_tr = excluded.description_tr,
    icon_key = excluded.icon_key,
    sort_order = excluded.sort_order,
    active = true,
    updated_at = current_timestamp,
    updated_by = 'migration-v323';

create table food_brand_category_rules (
    id bigserial primary key,
    normalized_brand_key varchar(255) not null,
    category_id bigint not null references food_categories(id),
    active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint uk_food_brand_category_rules unique(normalized_brand_key, category_id)
);

create index idx_food_brand_category_rules_active
    on food_brand_category_rules(active, normalized_brand_key, category_id);

insert into food_brand_category_rules(
    normalized_brand_key, category_id, active, created_by, updated_by
)
select seed.brand_key, category.id, true, 'migration-v323', 'migration-v323'
from (values
    ('kfc'),
    ('mcdonalds'),
    ('burgerking'),
    ('pizzahut'),
    ('dominos'),
    ('subway'),
    ('popeyes'),
    ('fiveguys'),
    ('tacobell')
) seed(brand_key)
join food_categories category on category.slug = 'fast-food-takeaway'
on conflict (normalized_brand_key, category_id) do nothing;

create or replace function grun_normalized_brand_key(value text)
returns text
language sql
immutable
strict
as $$
    select replace(replace(replace(replace(replace(replace(replace(replace(replace(
        translate(lower(value),
            'àáâãäåçèéêëìíîïñòóôõöùúûüýÿğışşčćžźż',
            'aaaaaaceeeeiiiinooooouuuuyygisscczzz'),
        ' ', ''), '-', ''), '•', ''), '·', ''), '‐', ''), '‑', ''), '–', ''), '—', ''), ' ', '')
$$;

create or replace function sync_food_brand_secondary_categories()
returns trigger
language plpgsql
as $$
declare
    resolved_brand_key text;
begin
    select coalesce(brand_row.normalized_key, grun_normalized_brand_key(new.brand))
      into resolved_brand_key
      from (select 1) anchor
      left join food_brands brand_row on brand_row.id = new.brand_id;

    delete from food_item_categories assignment
    using food_brand_category_rules rule
    where assignment.food_item_id = new.id
      and assignment.category_id = rule.category_id
      and assignment.primary_category = false
      and assignment.assignment_source = 'SOURCE_MAPPING'
      and assignment.created_by = 'brand-category-rule'
      and (not rule.active or resolved_brand_key is null
           or rule.normalized_brand_key <> resolved_brand_key);

    if resolved_brand_key is null then
        return new;
    end if;

    insert into food_item_categories(
        food_item_id, category_id, primary_category, assignment_source,
        confidence_score, reviewed, created_at, updated_at, created_by, updated_by
    )
    select new.id, rule.category_id, false, 'SOURCE_MAPPING',
           100, true, current_timestamp, current_timestamp,
           'brand-category-rule', 'brand-category-rule'
    from food_brand_category_rules rule
    where rule.active
      and rule.normalized_brand_key = resolved_brand_key
    on conflict (food_item_id, category_id) do nothing;

    return new;
end;
$$;

create trigger trg_food_items_brand_secondary_categories
after insert or update of brand, brand_id on food_items
for each row execute function sync_food_brand_secondary_categories();

-- Attach the secondary category to existing catalog products without replacing their primary category.
insert into food_item_categories(
    food_item_id, category_id, primary_category, assignment_source,
    confidence_score, reviewed, created_at, updated_at, created_by, updated_by
)
select item.id, rule.category_id, false, 'SOURCE_MAPPING',
       100, true, current_timestamp, current_timestamp,
       'brand-category-rule', 'brand-category-rule'
from food_items item
left join food_brands brand_row on brand_row.id = item.brand_id
join food_brand_category_rules rule
  on rule.active
 and rule.normalized_brand_key = coalesce(
     brand_row.normalized_key,
     grun_normalized_brand_key(item.brand)
 )
on conflict (food_item_id, category_id) do nothing;
