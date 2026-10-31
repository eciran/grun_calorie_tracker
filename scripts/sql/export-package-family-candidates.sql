BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s';
SET LOCAL lock_timeout='3s';
SELECT json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false)));
SELECT json_build_object('kind','row','row',jsonb_build_object(
 'id',f.id,'name',f.name,'display_name',f.display_name,'brand',f.brand,
 'brand_id',to_jsonb(f)->>'brand_id','catalog_type',f.catalog_type,
 'barcode',f.barcode,'source_key',f.source_key,'market_region',f.market_region,
 'preparation_state',f.preparation_state,'nutrition_reference_unit',f.nutrition_reference_unit,
 'calories',f.calories,'protein',f.protein,'carbs',f.carbs,'fat',f.fat,
 'fiber',to_jsonb(f)->>'fiber','sugar',to_jsonb(f)->>'sugar',
 'serving_size_grams',f.serving_size_grams,'serving_unit',f.serving_unit,
 'publication_status',f.publication_status,'data_source',f.data_source,
 'package_quantity',to_jsonb(f)->>'package_quantity','package_unit',to_jsonb(f)->>'package_unit'
)) FROM food_items f WHERE NOT coalesce(f.is_custom,false) ORDER BY f.id;
SELECT json_build_object('kind','end','count',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false)));
ROLLBACK;
