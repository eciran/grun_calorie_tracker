-- All inbound FK relations are discovered, including composite/unsupported ones.
-- Row contents stay private: only aggregate counts and fingerprints leave PostgreSQL.
WITH refs AS (
 SELECT c.oid,c.conname,n.nspname,t.relname,a.attname,
 cardinality(c.conkey)=1 AND cardinality(c.confkey)=1 AND pa.attname='id' AS supported
 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
 JOIN pg_namespace n ON n.oid=t.relnamespace
 LEFT JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
 LEFT JOIN pg_attribute pa ON pa.attrelid=c.confrelid AND pa.attnum=c.confkey[1]
 WHERE c.contype='f' AND c.confrelid='food_items'::regclass
), measured AS (
 SELECT *,CASE WHEN supported THEN query_to_xml(format(
 'SELECT count(*) AS count, md5(coalesce(string_agg(md5(to_jsonb(r)::text), '''' ORDER BY md5(to_jsonb(r)::text)), '''')) AS fingerprint FROM %I.%I r WHERE %I IN (49682,181002)',
 nspname,relname,attname),false,true,'') ELSE NULL END AS proof FROM refs
)
SELECT jsonb_build_object('kind','metrics','values',jsonb_build_object(
 'historyLinks',(SELECT jsonb_agg(jsonb_build_object('id',f.id,
 'recipes',(SELECT count(*) FROM recipe_ingredients r WHERE r.food_item_id=f.id),
 'recipeSnapshotsNamedAsOldProduct',(SELECT count(*) FROM recipe_ingredients r
 WHERE r.food_item_id=f.id AND r.snapshot_food_name=f.name),
 'logs',(SELECT count(*) FROM food_logs l WHERE l.food_id=f.id),
 'logSnapshotsNamedAsOldProduct',(SELECT count(*) FROM food_logs l WHERE l.food_id=f.id AND l.display_name=f.name)) ORDER BY f.id)
 FROM food_items f WHERE f.id IN (49682,181002)),
 'identityDependencies',coalesce((SELECT jsonb_agg(jsonb_build_object(
 'constraint',conname,'schema',nspname,'table',relname,'column',attname,'supported',supported,
 'count',(xpath('/row/count/text()',proof))[1]::text,
 'fingerprint',(xpath('/row/fingerprint/text()',proof))[1]::text) ORDER BY nspname,relname,conname) FROM measured),'[]'::jsonb),
 'identityKeyClaims',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'source_key',source_key,
 'canonical_food_key',canonical_food_key,'is_custom',is_custom,'publication_status',publication_status) ORDER BY id)
 FROM food_items WHERE source_key IN ('USDA_FOODDATA:fdc:171443','USDA_FOODDATA:fdc:171491')
 OR canonical_food_key IN ('GLOBAL:GENERIC_INGREDIENT:PREPARED:olive_oil',
 'GLOBAL:GENERIC_INGREDIENT:ROASTED:turkey_breast_all_classes',
 'GLOBAL:GENERIC_INGREDIENT:PREPARED:reduced_fat_mayonnaise_with_olive_oil',
 'GLOBAL:GENERIC_INGREDIENT:ROASTED:turkey_light_meat_all_classes')),'[]'::jsonb),
 'canonicalResolutions',coalesce((SELECT jsonb_agg(jsonb_build_object('canonical_food_key',canonical_food_key,
 'primary_food_item_id',primary_food_item_id) ORDER BY canonical_food_key) FROM food_canonical_resolutions
 WHERE primary_food_item_id IN (49682,181002) OR canonical_food_key IN (
 'GLOBAL:GENERIC_INGREDIENT:PREPARED:olive_oil','GLOBAL:GENERIC_INGREDIENT:ROASTED:turkey_breast_all_classes',
 'GLOBAL:GENERIC_INGREDIENT:PREPARED:reduced_fat_mayonnaise_with_olive_oil',
 'GLOBAL:GENERIC_INGREDIENT:ROASTED:turkey_light_meat_all_classes')),'[]'::jsonb),
 'identityKeyIndexes',(SELECT jsonb_agg(jsonb_build_object('name',indexname,'definition',indexdef) ORDER BY indexname)
 FROM pg_indexes WHERE tablename='food_items' AND indexdef LIKE '%canonical_food_key%')));
