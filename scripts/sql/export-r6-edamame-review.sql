-- Exact identity/portion-state review. No private historical row contents exported.
WITH refs AS (
 SELECT c.conname,n.nspname,t.relname,a.attname,
 cardinality(c.conkey)=1 AND cardinality(c.confkey)=1 AND pa.attname='id' AS supported
 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
 JOIN pg_namespace n ON n.oid=t.relnamespace
 LEFT JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
 LEFT JOIN pg_attribute pa ON pa.attrelid=c.confrelid AND pa.attnum=c.confkey[1]
 WHERE c.contype='f' AND c.confrelid='food_items'::regclass
), measured AS (
 SELECT *,CASE WHEN supported THEN query_to_xml(format(
 'SELECT count(*) AS count, md5(coalesce(string_agg(md5(to_jsonb(r)::text), '''' ORDER BY md5(to_jsonb(r)::text)), '''')) AS fingerprint FROM %I.%I r WHERE %I=181467',
 nspname,relname,attname),false,true,'') ELSE NULL END AS proof FROM refs
)
SELECT jsonb_build_object('kind','metrics','values',jsonb_build_object(
 'dependencies',coalesce((SELECT jsonb_agg(jsonb_build_object(
 'constraint',conname,'schema',nspname,'table',relname,'column',attname,'supported',supported,
 'count',(xpath('/row/count/text()',proof))[1]::text,
 'fingerprint',(xpath('/row/fingerprint/text()',proof))[1]::text) ORDER BY nspname,relname,conname) FROM measured),'[]'::jsonb),
 'keyClaims',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'source_key',source_key,
 'canonical_food_key',canonical_food_key,'is_custom',is_custom,'publication_status',publication_status) ORDER BY id)
 FROM food_items WHERE source_key IN ('USDA_FOODDATA:fdc:168411','USDA:fdc:168411')
 OR canonical_food_key IN ('GLOBAL:GENERIC_INGREDIENT:BOILED:edamame',
 'GLOBAL:GENERIC_INGREDIENT:BOILED:frozen_edamame',
 'GLOBAL:GENERIC_INGREDIENT:PREPARED:edamame_from_frozen')),'[]'::jsonb),
 'canonicalResolutions',coalesce((SELECT jsonb_agg(to_jsonb(r) ORDER BY canonical_food_key)
 FROM food_canonical_resolutions r WHERE primary_food_item_id=181467 OR canonical_food_key IN (
 'GLOBAL:GENERIC_INGREDIENT:BOILED:edamame','GLOBAL:GENERIC_INGREDIENT:BOILED:frozen_edamame',
 'GLOBAL:GENERIC_INGREDIENT:PREPARED:edamame_from_frozen')),'[]'::jsonb),
 'keyIndexes',(SELECT jsonb_agg(jsonb_build_object('name',indexname,'definition',indexdef) ORDER BY indexname)
 FROM pg_indexes WHERE tablename='food_items' AND indexdef LIKE '%canonical_food_key%')));
