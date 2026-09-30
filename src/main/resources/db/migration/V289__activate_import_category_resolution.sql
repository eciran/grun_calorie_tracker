alter table food_category_source_mappings
    add column primary_priority integer not null default 1000;

create table food_category_resolution_rules (
    id bigserial primary key,
    rule_key varchar(160) not null unique,
    data_source varchar(40) not null,
    market_region varchar(30),
    source_review_category varchar(160) not null,
    target_category_id bigint not null references food_categories(id),
    status varchar(30) not null default 'REVIEW_REQUIRED',
    primary_priority integer not null default 1000,
    confidence_score integer,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    created_by varchar(255),
    updated_by varchar(255),
    constraint chk_food_category_resolution_status check (
        status in ('ACTIVE', 'IGNORED', 'REVIEW_REQUIRED')
    ),
    constraint chk_food_category_resolution_confidence check (
        confidence_score is null or confidence_score between 0 and 100
    )
);

create table food_category_resolution_required_tags (
    rule_id bigint not null references food_category_resolution_rules(id) on delete cascade,
    category_tag varchar(180) not null,
    primary key (rule_id, category_tag)
);

create table food_category_resolution_excluded_tags (
    rule_id bigint not null references food_category_resolution_rules(id) on delete cascade,
    category_tag varchar(180) not null,
    primary key (rule_id, category_tag)
);

create index idx_food_category_resolution_scope
    on food_category_resolution_rules(data_source, market_region, status, source_review_category);

with mapping_seed(source_tag, category_slug, primary_priority, status) as (
    values
        ('en:fruits', 'fruit', 510, 'ACTIVE'),
        ('en:vegetables', 'vegetables', 500, 'REVIEW_REQUIRED'),
        ('en:poultries', 'poultry', 410, 'ACTIVE'),
        ('en:chickens', 'poultry', 405, 'ACTIVE'),
        ('en:turkeys', 'poultry', 405, 'ACTIVE'),
        ('en:pork', 'red-meat', 420, 'REVIEW_REQUIRED'),
        ('en:prepared-meats', 'processed-meat', 300, 'ACTIVE'),
        ('en:sausages', 'processed-meat', 290, 'ACTIVE'),
        ('en:hams', 'processed-meat', 285, 'ACTIVE'),
        ('en:fishes', 'fish', 400, 'ACTIVE'),
        ('en:fish-fillets', 'fish', 390, 'ACTIVE'),
        ('en:canned-fishes', 'fish', 380, 'ACTIVE'),
        ('en:milks', 'milk', 400, 'REVIEW_REQUIRED'),
        ('en:cheeses', 'cheese', 390, 'ACTIVE'),
        ('en:yogurts', 'yogurt-cultured-dairy', 380, 'ACTIVE'),
        ('en:breads', 'bread-bakery-staples', 410, 'REVIEW_REQUIRED'),
        ('en:rices', 'rice-grains', 400, 'ACTIVE'),
        ('en:pastas', 'pasta-noodles', 350, 'ACTIVE'),
        ('en:noodles', 'pasta-noodles', 345, 'ACTIVE'),
        ('en:breakfast-cereals', 'breakfast-cereals', 370, 'ACTIVE'),
        ('en:legumes', 'legumes-pulses', 410, 'ACTIVE'),
        ('en:pulses', 'legumes-pulses', 405, 'ACTIVE'),
        ('en:nuts', 'nuts-seeds', 400, 'ACTIVE'),
        ('en:soups', 'soups', 120, 'ACTIVE'),
        ('en:pizzas', 'pizza-pies', 100, 'REVIEW_REQUIRED'),
        ('en:pizzas-pies-and-quiches', 'pizza-pies', 105, 'REVIEW_REQUIRED'),
        ('en:sandwiches', 'sandwiches-wraps', 110, 'REVIEW_REQUIRED'),
        ('en:microwave-meals', 'ready-meals', 115, 'ACTIVE'),
        ('en:meals-with-meat', 'ready-meals', 118, 'ACTIVE'),
        ('en:meals-with-chicken', 'ready-meals', 116, 'ACTIVE'),
        ('en:salty-snacks', 'savoury-snacks', 210, 'ACTIVE'),
        ('en:crisps', 'savoury-snacks', 200, 'ACTIVE'),
        ('en:potato-crisps', 'savoury-snacks', 195, 'ACTIVE'),
        ('en:biscuits', 'biscuits-cakes', 185, 'ACTIVE'),
        ('en:cakes', 'biscuits-cakes', 180, 'ACTIVE'),
        ('en:chocolate-biscuits', 'biscuits-cakes', 170, 'ACTIVE'),
        ('en:chocolates', 'chocolate-confectionery', 220, 'ACTIVE'),
        ('en:chocolate-candies', 'chocolate-confectionery', 215, 'ACTIVE'),
        ('en:candies', 'chocolate-confectionery', 225, 'ACTIVE'),
        ('en:ice-creams', 'desserts-ice-cream', 205, 'ACTIVE'),
        ('en:ice-creams-and-sorbets', 'desserts-ice-cream', 200, 'ACTIVE'),
        ('en:carbonated-drinks', 'soft-drinks', 310, 'ACTIVE'),
        ('en:sodas', 'soft-drinks', 305, 'ACTIVE'),
        ('en:fruit-juices', 'juice', 300, 'ACTIVE'),
        ('en:juices-and-nectars', 'juice', 305, 'ACTIVE'),
        ('en:hot-beverages', 'hot-drinks', 320, 'REVIEW_REQUIRED'),
        ('en:milk-substitutes', 'plant-based-drinks', 290, 'REVIEW_REQUIRED'),
        ('en:plant-based-milk-alternatives', 'plant-based-drinks', 285, 'REVIEW_REQUIRED'),
        ('en:sauces', 'sauces-condiments', 340, 'ACTIVE'),
        ('en:tomato-sauces', 'sauces-condiments', 330, 'ACTIVE'),
        ('en:dips', 'spreads-dips', 325, 'ACTIVE'),
        ('en:spreads', 'spreads-dips', 335, 'ACTIVE'),
        ('en:vegetable-oils', 'cooking-oils', 350, 'ACTIVE'),
        ('en:dietary-supplements', 'dietary-supplements', 250, 'REVIEW_REQUIRED'),
        ('en:bodybuilding-supplements', 'sports-nutrition', 240, 'REVIEW_REQUIRED')
), updated as (
    update food_category_source_mappings mapping
       set category_id = category.id,
           source_tag = seed.source_tag,
           status = seed.status,
           primary_priority = seed.primary_priority,
           confidence_score = 100,
           updated_at = current_timestamp,
           updated_by = 'flyway-v289'
      from mapping_seed seed
      join food_categories category on category.slug = seed.category_slug
     where mapping.data_source = 'OPEN_FOOD_FACTS'
       and mapping.normalized_source_tag = seed.source_tag
       and mapping.market_region is null
    returning mapping.id
)
insert into food_category_source_mappings (
    data_source, source_tag, normalized_source_tag, market_region, category_id,
    status, primary_priority, confidence_score, created_by, updated_by
)
select 'OPEN_FOOD_FACTS', seed.source_tag, seed.source_tag, null, category.id,
       seed.status, seed.primary_priority, 100, 'flyway-v289', 'flyway-v289'
  from mapping_seed seed
  join food_categories category on category.slug = seed.category_slug
 where not exists (
     select 1 from food_category_source_mappings mapping
      where mapping.data_source = 'OPEN_FOOD_FACTS'
        and mapping.normalized_source_tag = seed.source_tag
        and mapping.market_region is null
 );

with rule_seed(rule_key, source_review_category, target_slug, primary_priority) as (
    values
        ('milk-explicit-subtypes', 'milk', 'milk', 400),
        ('bread-explicit-subtypes', 'bread-bakery-staples', 'bread-bakery-staples', 410),
        ('pizza-pie-explicit-subtypes', 'pizza-pies', 'pizza-pies', 100),
        ('sandwich-wrap-explicit-subtypes', 'sandwiches-wraps', 'sandwiches-wraps', 110),
        ('pork-explicit-cuts', 'red-meat', 'red-meat', 420),
        ('pork-bacon-to-processed-meat', 'red-meat', 'processed-meat', 300),
        ('tea-coffee-explicit-subtypes', 'hot-drinks', 'hot-drinks', 320),
        ('sports-protein-powder-shake', 'sports-nutrition', 'sports-nutrition', 240),
        ('supplement-explicit-subtypes', 'dietary-supplements', 'dietary-supplements', 250),
        ('bread-residual-explicit-subtypes', 'bread-bakery-staples', 'bread-bakery-staples', 410),
        ('vegetable-residual-explicit-subtypes', 'vegetables', 'vegetables', 500),
        ('sports-residual-explicit-bars', 'sports-nutrition', 'sports-nutrition', 240),
        ('pizza-pie-residual-explicit-subtypes', 'pizza-pies', 'pizza-pies', 100),
        ('sandwich-residual-explicit-subtypes', 'sandwiches-wraps', 'sandwiches-wraps', 110),
        ('milk-residual-explicit-subtypes', 'milk', 'milk', 400),
        ('pork-residual-explicit-subtypes', 'red-meat', 'red-meat', 420),
        ('hot-drink-residual-cocoa', 'hot-drinks', 'hot-drinks', 320)
)
insert into food_category_resolution_rules (
    rule_key, data_source, source_review_category, target_category_id, status,
    primary_priority, confidence_score, created_by, updated_by
)
select seed.rule_key, 'OPEN_FOOD_FACTS', seed.source_review_category, category.id,
       'ACTIVE', seed.primary_priority, 95, 'flyway-v289', 'flyway-v289'
  from rule_seed seed
  join food_categories category on category.slug = seed.target_slug;

with required_seed(rule_key, tags) as (
    values
        ('milk-explicit-subtypes', array['en:semi-skimmed-milks','en:whole-milks','en:skimmed-milks','en:uht-milks','en:lactose-free-milk','en:cow-milks','en:pasteurised-milks','en:milk-powders']),
        ('bread-explicit-subtypes', array['en:sliced-breads','en:flatbreads','en:white-breads','en:wholemeal-breads','en:wheat-breads','en:baguettes','en:bagel-breads','en:bread-rolls','en:breadsticks','en:sourdough']),
        ('pizza-pie-explicit-subtypes', array['en:frozen-pizzas','en:vegetarian-pizzas','en:pies','en:quiches','en:cheese-pizzas','en:pepperoni-pizzas','en:margherita-pizza','en:meat-pies','en:pork-pies']),
        ('sandwich-wrap-explicit-subtypes', array['en:wraps','en:chicken-wraps','en:sandwiches-filled-with-cold-cuts','en:poultry-sandwiches','en:chicken-sandwiches','en:sandwich-made-with-loaf-bread','en:cheese-sandwiches','en:ham-sandwiches','en:burritos','en:sandwich-wrap']),
        ('pork-explicit-cuts', array['en:pork-ribs','en:pork-roasts','en:pork-filet-mignon','en:pork-loin','en:pork-belly']),
        ('pork-bacon-to-processed-meat', array['en:bacon','en:bacon-rashers','en:back-bacon','en:smoked-bacon','en:unsmoked-bacon','en:smoked-back-bacon','en:unsmoked-back-bacon']),
        ('tea-coffee-explicit-subtypes', array['en:teas','en:herbal-teas','en:tea-bags','en:green-teas','en:black-teas','en:oolong-teas','en:tea-leaves','en:coffees','en:coffee-capsules']),
        ('sports-protein-powder-shake', array['en:protein-powders','en:protein-shakes']),
        ('supplement-explicit-subtypes', array['en:vitamins','en:spirulina','en:collagen','en:folic-acid']),
        ('bread-residual-explicit-subtypes', array['en:special-breads','en:hamburger-buns','en:crispbreads','en:gluten-free-breads','en:bread-crumbs','en:rye-breads','en:toasts','en:brioches','en:sourdough-bread','en:sourdough-breads','en:english-muffins','en:ciabatta','en:hot-dog-buns','en:breads-with-corn','en:naan-bread']),
        ('vegetable-residual-explicit-subtypes', array['en:tomatoes','en:canned-vegetables','en:fresh-vegetables','en:mixed-vegetables','en:carrots','en:frozen-vegetables','en:lettuces','en:broccoli','en:sweet-peppers','en:spinachs','en:pickles']),
        ('sports-residual-explicit-bars', array['en:protein-bars','en:protein-energy-bars','en:energy-bars','en:protein-balls','en:protein-water']),
        ('pizza-pie-residual-explicit-subtypes', array['en:pizza-with-ham-and-cheese','en:chicken-pizza','en:frozen-pizzas-and-pies','en:salted-pies','en:salmon-pizza','en:ham-pizzas','en:mini-appetizer-pizzas','en:chorizo-pizzas','en:frozen-pies','en:cheese-pies','en:regina-pizza','en:cured-ham-pizza','en:prosciutto-pizza','en:pizzas-with-ham']),
        ('sandwich-residual-explicit-subtypes', array['en:tuna-sandwiches','en:paninis','en:salmon-sandwiches']),
        ('milk-residual-explicit-subtypes', array['en:dairy-drinks','en:flavoured-milks','en:evaporated-milks','en:chocolate-milks','en:goat-milks','en:fresh-milks','en:milkshakes','en:condensed-milks','en:buttermilks','en:vanilla-milks','en:strawberry-milks','en:sheep-milks']),
        ('pork-residual-explicit-subtypes', array['en:pork-shoulders','en:pork-knuckle','en:ground-pork-meat','en:pork-skewers','en:pork-spare-ribs']),
        ('hot-drink-residual-cocoa', array['en:cocoa-and-its-products','en:cocoa-and-chocolate-powders','en:instant-chocolate-powders'])
)
insert into food_category_resolution_required_tags(rule_id, category_tag)
select rule.id, tag
  from required_seed seed
  join food_category_resolution_rules rule on rule.rule_key = seed.rule_key
 cross join lateral unnest(seed.tags) tag;

with excluded_seed(rule_key, tags) as (
    values
        ('sandwich-wrap-explicit-subtypes', array['en:hamburgers','en:beef-hamburgers']),
        ('pork-explicit-cuts', array['en:bacon','en:bacon-rashers','en:back-bacon','en:beef','en:beef-and-its-products']),
        ('tea-coffee-explicit-subtypes', array['en:iced-teas']),
        ('sports-protein-powder-shake', array['en:meal-replacements','en:nutritionally-complete-food']),
        ('supplement-explicit-subtypes', array['en:meal-replacements']),
        ('bread-residual-explicit-subtypes', array['en:flours','en:cereal-flours']),
        ('vegetable-residual-explicit-subtypes', array['en:meals','en:prepared-salads','en:rice']),
        ('sports-residual-explicit-bars', array['en:breakfast-cereals','en:meal-replacements','en:nutritionally-complete-food']),
        ('pizza-pie-residual-explicit-subtypes', array['en:pizza-sauces']),
        ('sandwich-residual-explicit-subtypes', array['en:hamburgers','en:beef-hamburgers','en:chicken-hamburgers','en:vegetarian-hamburgers','en:doner-kebab']),
        ('milk-residual-explicit-subtypes', array['en:plant-based-foods','en:plant-based-beverages','en:coconut-milks-and-creams']),
        ('pork-residual-explicit-subtypes', array['en:beef','en:beef-and-its-products','en:cooked-meats','en:pancetta','en:gammon']),
        ('hot-drink-residual-cocoa', array['en:iced-teas','en:flavored-waters','en:waters'])
)
insert into food_category_resolution_excluded_tags(rule_id, category_tag)
select rule.id, tag
  from excluded_seed seed
  join food_category_resolution_rules rule on rule.rule_key = seed.rule_key
 cross join lateral unnest(seed.tags) tag;
