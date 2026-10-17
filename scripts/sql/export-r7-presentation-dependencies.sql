-- Anonymous dependency fingerprints and exact identity/category namespace only.
WITH selected AS MATERIALIZED (SELECT * FROM food_items WHERE id IN (49340,181013,181138,181140,181155)),
targets(concept,variant,canonical) AS (VALUES
 ('chicken_light_meat','fdc_171466','C2:GENERIC_INGREDIENT:ROASTED:chicken_light_meat:fdc_171466'),
 ('broccoli','fdc_170380','C2:GENERIC_INGREDIENT:BOILED:broccoli:fdc_170380'),
 ('broccoli','fdc_169969','C2:GENERIC_INGREDIENT:BOILED:broccoli:fdc_169969'),
 ('broccoli','fdc_168510','C2:GENERIC_INGREDIENT:BOILED:broccoli:fdc_168510')
), refs AS (
 SELECT c.conname,n.nspname,t.relname,a.attname,
 cardinality(c.conkey)=1 AND cardinality(c.confkey)=1 AND pa.attname='id' AS supported
 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
 LEFT JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
 LEFT JOIN pg_attribute pa ON pa.attrelid=c.confrelid AND pa.attnum=c.confkey[1]
 WHERE c.contype='f' AND c.confrelid='food_items'::regclass
), measured AS (
 SELECT *,CASE WHEN supported THEN query_to_xml(format(
 'SELECT count(*) AS count,md5(coalesce(string_agg(md5(to_jsonb(r)::text), '''' ORDER BY md5(to_jsonb(r)::text)), '''')) AS fingerprint FROM %I.%I r WHERE %I IN (49340,181013,181138,181140,181155)',
 nspname,relname,attname),false,true,'') ELSE NULL END AS proof FROM refs
)
SELECT jsonb_build_object('kind','metrics','values',jsonb_build_object(
 'dependencies',coalesce((SELECT jsonb_agg(jsonb_build_object('constraint',conname,'schema',nspname,'table',relname,
 'column',attname,'supported',supported,'count',(xpath('/row/count/text()',proof))[1]::text,
 'fingerprint',(xpath('/row/fingerprint/text()',proof))[1]::text) ORDER BY nspname,relname,conname) FROM measured),'[]'::jsonb),
 'keyClaims',coalesce((SELECT jsonb_agg(jsonb_build_object('id',f.id,'source_key',f.source_key,
 'canonical_food_key',f.canonical_food_key,'food_concept_key',f.food_concept_key,
 'composition_variant_key',f.composition_variant_key,'is_custom',f.is_custom,'publication_status',f.publication_status) ORDER BY f.id)
 FROM food_items f WHERE f.id IN (SELECT id FROM selected) OR f.source_key IN (SELECT source_key FROM selected)
 OR f.source_key IN (SELECT replace(source_key,'USDA_FOODDATA:','USDA:') FROM selected)
 OR f.canonical_food_key IN (SELECT canonical FROM targets)
 OR EXISTS(SELECT 1 FROM targets t WHERE f.food_concept_key=t.concept AND f.composition_variant_key=t.variant)),'[]'::jsonb),
 'resolutions',coalesce((SELECT jsonb_agg(to_jsonb(r) ORDER BY canonical_food_key) FROM food_canonical_resolutions r
 WHERE primary_food_item_id IN (SELECT id FROM selected) OR canonical_food_key IN (SELECT canonical FROM targets)),'[]'::jsonb),
 'indexes',(SELECT jsonb_agg(jsonb_build_object('name',indexname,'definition',indexdef) ORDER BY indexname)
 FROM pg_indexes WHERE tablename='food_items' AND (indexdef LIKE '%canonical_food_key%' OR indexdef LIKE '%food_concept_key%')),
 'categories',(SELECT jsonb_agg(to_jsonb(c) ORDER BY id) FROM food_categories c)));
