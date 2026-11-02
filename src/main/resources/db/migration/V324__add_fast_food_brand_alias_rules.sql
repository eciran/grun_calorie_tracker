-- Preserve exact reviewed brand keys that legitimately contain apostrophes.
-- Kept separate from V323 so deployed Flyway checksums remain immutable.
insert into food_brand_category_rules(
    normalized_brand_key, category_id, active, created_by, updated_by
)
select seed.brand_key, category.id, true, 'migration-v324', 'migration-v324'
from (values
    ('mcdonald''s'),
    ('mcdonald’s'),
    ('domino''s'),
    ('domino’s')
) seed(brand_key)
join food_categories category on category.slug = 'fast-food-takeaway'
on conflict (normalized_brand_key, category_id) do nothing;

-- Backfill the newly reviewed aliases while preserving every existing primary category.
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
where rule.created_by = 'migration-v324'
on conflict (food_item_id, category_id) do nothing;
