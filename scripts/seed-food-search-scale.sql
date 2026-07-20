insert into food_items (
    name, display_name, short_display_name, source_key, catalog_type,
    market_region, verification_status, is_custom, calories, protein, fat, carbs,
    quality_score, usage_count, data_source, preparation_state
)
select
    case mod(g, 1000)
        when 0 then 'Raw Banana Benchmark ' || g
        when 1 then 'Raw Broccoli Benchmark ' || g
        when 2 then 'Whole Milk Benchmark ' || g
        when 3 then 'Raw Chicken Breast Benchmark ' || g
        when 4 then 'Cooked Rice Benchmark ' || g
        else 'Benchmark Product ' || g
    end,
    case mod(g, 1000)
        when 0 then 'Raw Banana ' || g
        when 1 then 'Raw Broccoli ' || g
        when 2 then 'Whole Milk ' || g
        when 3 then 'Raw Chicken Breast ' || g
        when 4 then 'Cooked Rice ' || g
        else 'Benchmark Product ' || g
    end,
    case mod(g, 1000)
        when 0 then 'Banana ' || g
        when 1 then 'Broccoli ' || g
        when 2 then 'Milk ' || g
        when 3 then 'Chicken Breast ' || g
        when 4 then 'Rice ' || g
        else 'Benchmark Product ' || g
    end,
    'S11:' || g,
    case when mod(g, 1000) between 0 and 4 then 'GENERIC_INGREDIENT' else 'BRANDED_PRODUCT' end,
    'UK_IE', 'VERIFIED', false, 100 + mod(g, 200), 5, 2, 18, 80, mod(g, 10000),
    case when mod(g, 1000) between 0 and 4 then 'USDA_FOODDATA' else 'OPEN_FOOD_FACTS' end,
    case mod(g, 1000)
        when 0 then 'RAW'
        when 1 then 'RAW'
        when 2 then 'PREPARED'
        when 3 then 'RAW'
        when 4 then 'COOKED'
        else 'UNSPECIFIED'
    end
from generate_series(:first, :last) as g;

insert into food_item_market_regions (food_item_id, market_region)
select fi.id, 'UK_IE'
from food_items fi
join generate_series(:first, :last) as g on fi.source_key = 'S11:' || g
on conflict do nothing;

insert into food_item_market_regions (food_item_id, market_region)
select fi.id, 'TR'
from food_items fi
join generate_series(:first, :last) as g on fi.source_key = 'S11:' || g
where mod(g, 1000) between 0 and 4
on conflict do nothing;

insert into food_item_localizations (
    food_item_id, language, display_name, short_display_name, source, active
)
select
    fi.id, 'TR',
    case mod(g, 1000)
        when 0 then 'Çiğ Muz ' || fi.id
        when 1 then 'Çiğ Brokoli ' || fi.id
        when 2 then 'Tam Yağlı Süt ' || fi.id
        when 3 then 'Çiğ Tavuk Göğsü ' || fi.id
        when 4 then 'Pişmiş Pirinç ' || fi.id
    end,
    case mod(g, 1000)
        when 0 then 'Muz ' || fi.id
        when 1 then 'Brokoli ' || fi.id
        when 2 then 'Süt ' || fi.id
        when 3 then 'Tavuk Göğsü ' || fi.id
        when 4 then 'Pirinç ' || fi.id
    end,
    'S11_SCALE', true
from food_items fi
join generate_series(:first, :last) as g on fi.source_key = 'S11:' || g
where mod(g, 1000) between 0 and 4
on conflict (food_item_id, language) do nothing;

analyze food_items;
analyze food_item_localizations;
analyze food_item_market_regions;
