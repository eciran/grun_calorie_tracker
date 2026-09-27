create table recipe_translations (
    id bigserial primary key,
    recipe_id bigint not null references recipes(id) on delete cascade,
    language varchar(12) not null,
    name varchar(160) not null,
    description varchar(1000),
    constraint uk_recipe_translations_recipe_language unique (recipe_id, language),
    constraint chk_recipe_translations_language check (language in ('EN', 'TR'))
);

create index idx_recipe_translations_language_name
    on recipe_translations (language, lower(name));

create table recipe_translation_steps (
    id bigserial primary key,
    translation_id bigint not null references recipe_translations(id) on delete cascade,
    step_order integer not null,
    instruction varchar(1000) not null,
    constraint uk_recipe_translation_steps_order unique (translation_id, step_order)
);

create table recipe_market_regions (
    recipe_id bigint not null references recipes(id) on delete cascade,
    market_region varchar(24) not null,
    primary key (recipe_id, market_region),
    constraint chk_recipe_market_regions_region check (market_region in ('GLOBAL', 'TR', 'UK_IE', 'EU'))
);

insert into recipe_market_regions (recipe_id, market_region)
select id, coalesce(market_region, 'GLOBAL')
from recipes
on conflict do nothing;

insert into recipe_translations (recipe_id, language, name, description)
select id,
       case when lower(coalesce(language, 'en')) like 'tr%' then 'TR' else 'EN' end,
       name,
       description
from recipes
on conflict do nothing;

insert into recipe_translation_steps (translation_id, step_order, instruction)
select translation.id, step.step_order, step.instruction
from recipe_cooking_steps step
join recipe_translations translation on translation.recipe_id = step.recipe_id
on conflict do nothing;
