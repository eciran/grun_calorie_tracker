with rule_seed(rule_key, source_review_category, target_slug, primary_priority) as (
    values
        ('bread-residual-exact-subtypes-v3', 'bread-bakery-staples', 'bread-bakery-staples', 410),
        ('vegetable-residual-exact-produce-v3', 'vegetables', 'vegetables', 500),
        ('pizza-residual-seafood-subtype-v3', 'pizza-pies', 'pizza-pies', 100),
        ('sandwich-residual-exact-subtypes-v3', 'sandwiches-wraps', 'sandwiches-wraps', 110)
)
insert into food_category_resolution_rules (
    rule_key, data_source, source_review_category, target_category_id, status,
    primary_priority, confidence_score, created_by, updated_by
)
select seed.rule_key, 'OPEN_FOOD_FACTS', seed.source_review_category, category.id,
       'ACTIVE', seed.primary_priority, 97, 'flyway-v291', 'flyway-v291'
from rule_seed seed
join food_categories category on category.slug = seed.target_slug;

with required_seed(rule_key, tags) as (
    values
        ('bread-residual-exact-subtypes-v3', array[
            'en:pains-de-campagne','en:hot-cross-buns','en:focaccia','en:unleavened-breads',
            'en:crumpets','en:seeded-bread','en:soda-bread','en:sourdough-loaf'
        ]),
        ('vegetable-residual-exact-produce-v3', array[
            'en:asparagus','en:artichokes','en:artichoke-hearts','en:aubergines','en:beet',
            'en:beetroot','en:broccoli','en:brussels-sprouts','en:cabbages','en:cucumbers',
            'en:leeks','en:parsnip','en:radishes','en:red-cabbage','en:rocket','en:sweet-potatoes'
        ]),
        ('pizza-residual-seafood-subtype-v3', array['en:seafood-pizza']),
        ('sandwich-residual-exact-subtypes-v3', array['en:fish-sandwiches'])
)
insert into food_category_resolution_required_tags(rule_id, category_tag)
select rule.id, tag
from required_seed seed
join food_category_resolution_rules rule on rule.rule_key = seed.rule_key
cross join lateral unnest(seed.tags) tag;

with excluded_seed(rule_key, tags) as (
    values
        ('bread-residual-exact-subtypes-v3', array[
            'en:flours','en:cereal-flours','en:cereal-bars','en:croutons','en:bread-mix'
        ]),
        ('vegetable-residual-exact-produce-v3', array[
            'en:meals','en:prepared-salads','en:rice','en:condiments','en:culinary-plants',
            'en:hamburgers','en:vegetarian-hamburgers'
        ]),
        ('pizza-residual-seafood-subtype-v3', array['en:pizza-sauces','en:pizza-dough']),
        ('sandwich-residual-exact-subtypes-v3', array[
            'en:hamburgers','en:beef-hamburgers','en:chicken-hamburgers',
            'en:vegetarian-hamburgers','en:doner-kebab'
        ])
)
insert into food_category_resolution_excluded_tags(rule_id, category_tag)
select rule.id, tag
from excluded_seed seed
join food_category_resolution_rules rule on rule.rule_key = seed.rule_key
cross join lateral unnest(seed.tags) tag;
