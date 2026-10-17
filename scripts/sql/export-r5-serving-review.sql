-- One frozen, product-only snapshot; no catalog or service mutation.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s';
SET LOCAL lock_timeout='3s';
SELECT json_build_object('kind','metrics','values',json_build_object(
  'nonCustom',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false)),
  'published',(SELECT count(*) FROM food_items WHERE NOT coalesce(is_custom,false) AND publication_status='PUBLISHED'),
  'schemaVersion',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1)));

-- Tags select candidates only. They never authorize a basis conversion.
WITH coverage AS (
 SELECT food_item_id,bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND gram_weight>0 AND gram_weight::text NOT IN ('NaN','Infinity','-Infinity')) AS mass,
 bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND ml_volume>0 AND ml_volume::text NOT IN ('NaN','Infinity','-Infinity')) AS volume
 FROM food_item_serving_options GROUP BY food_item_id
), candidates AS (
 SELECT f.id FROM food_items f LEFT JOIN coverage o ON o.food_item_id=f.id
 WHERE NOT coalesce(f.is_custom,false) AND (f.id IN (181083,181084,181105,181122,181124,181144)
 OR (f.publication_status='PUBLISHED' AND (
 f.nutrition_reference_unit='PER_100ML'
 OR NOT CASE WHEN f.nutrition_reference_unit='PER_100ML' THEN coalesce(o.volume,false) ELSE coalesce(o.mass,false) END
 OR EXISTS (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id AND c.category_tag IN
 ('en:beverages','en:non-alcoholic-beverages','en:alcoholic-beverages','en:milks','en:plant-based-milks')))))
)
SELECT json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),'count',count(*)) FROM candidates;

WITH coverage AS (
 SELECT food_item_id,bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND gram_weight>0 AND gram_weight::text NOT IN ('NaN','Infinity','-Infinity')) AS mass,
 bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND ml_volume>0 AND ml_volume::text NOT IN ('NaN','Infinity','-Infinity')) AS volume
 FROM food_item_serving_options GROUP BY food_item_id
), candidates AS (
 SELECT f.* FROM food_items f LEFT JOIN coverage o ON o.food_item_id=f.id
 WHERE NOT coalesce(f.is_custom,false) AND (f.id IN (181083,181084,181105,181122,181124,181144)
 OR (f.publication_status='PUBLISHED' AND (
 f.nutrition_reference_unit='PER_100ML'
 OR NOT CASE WHEN f.nutrition_reference_unit='PER_100ML' THEN coalesce(o.volume,false) ELSE coalesce(o.mass,false) END
 OR EXISTS (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id AND c.category_tag IN
 ('en:beverages','en:non-alcoholic-beverages','en:alcoholic-beverages','en:milks','en:plant-based-milks')))))
)
SELECT json_build_object('kind','row','row',json_build_object(
 'id',f.id,'source_key',f.source_key,'data_source',f.data_source,'catalog_type',f.catalog_type,
 'name',f.name,'display_name',f.display_name,'preparation_state',f.preparation_state,
 'nutrition_reference_unit',f.nutrition_reference_unit,'barcode',f.barcode,'normalized_barcode',f.normalized_barcode,
 'publication_status',f.publication_status,'serving_unit',f.serving_unit,'serving_size_grams',f.serving_size_grams,
 'calories',f.calories,'protein',f.protein,'fat',f.fat,'carbs',f.carbs,
 'categories',coalesce((SELECT json_agg(c.category_tag ORDER BY c.category_tag) FROM food_item_source_categories c WHERE c.food_item_id=f.id),'[]'::json),
 'options',coalesce((SELECT json_agg(to_jsonb(o) ORDER BY o.id) FROM food_item_serving_options o WHERE o.food_item_id=f.id),'[]'::json),
 'option_localizations',coalesce((SELECT json_agg(to_jsonb(l) ORDER BY l.id) FROM food_item_serving_option_localizations l
   JOIN food_item_serving_options o ON o.id=l.serving_option_id WHERE o.food_item_id=f.id),'[]'::json),
 'evidence',CASE WHEN f.nutrition_reference_unit='PER_100ML' OR f.id IN (181083,181084,181105,181122,181124,181144)
   OR EXISTS (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id AND c.category_tag IN
     ('en:beverages','en:non-alcoholic-beverages','en:alcoholic-beverages','en:milks','en:plant-based-milks'))
 THEN coalesce((SELECT json_agg(json_build_object('id',e.id,'provider',e.provider,'external_id',e.external_id,
   'field_name',e.field_name,'numeric_value',e.numeric_value,'basis',e.basis,'source_version',e.source_version,
   'observed_at',e.observed_at,'fingerprint',e.fingerprint) ORDER BY e.id)
   FROM food_product_source_evidence e WHERE e.food_item_id=f.id AND e.field_name IN ('CALORIES','PROTEIN','FAT','CARBS')),'[]'::json)
 ELSE '[]'::json END)) FROM candidates f ORDER BY f.id;
-- The end marker checks the number emitted by the identical frozen predicate.
WITH coverage AS (
 SELECT food_item_id,bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND gram_weight>0 AND gram_weight::text NOT IN ('NaN','Infinity','-Infinity')) AS mass,
 bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0
   AND quantity::text NOT IN ('NaN','Infinity','-Infinity')
   AND ml_volume>0 AND ml_volume::text NOT IN ('NaN','Infinity','-Infinity')) AS volume
 FROM food_item_serving_options GROUP BY food_item_id
)
SELECT json_build_object('kind','end','count',count(*)) FROM food_items f LEFT JOIN coverage o ON o.food_item_id=f.id
 WHERE NOT coalesce(f.is_custom,false) AND (f.id IN (181083,181084,181105,181122,181124,181144)
 OR (f.publication_status='PUBLISHED' AND (
 f.nutrition_reference_unit='PER_100ML'
 OR NOT CASE WHEN f.nutrition_reference_unit='PER_100ML' THEN coalesce(o.volume,false) ELSE coalesce(o.mass,false) END
 OR EXISTS (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id AND c.category_tag IN
 ('en:beverages','en:non-alcoholic-beverages','en:alcoholic-beverages','en:milks','en:plant-based-milks')))));
ROLLBACK;
