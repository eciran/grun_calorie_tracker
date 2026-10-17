-- Aggregate serving-option consumers only. No private row contents exported.
WITH refs AS (
 SELECT c.conname,n.nspname,t.relname,a.attname,
 cardinality(c.conkey)=1 AND cardinality(c.confkey)=1 AND pa.attname='id' AS supported
 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
 LEFT JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
 LEFT JOIN pg_attribute pa ON pa.attrelid=c.confrelid AND pa.attnum=c.confkey[1]
 WHERE c.contype='f' AND c.confrelid='food_item_serving_options'::regclass
), measured AS (
 SELECT *,CASE WHEN supported THEN query_to_xml(format(
 'SELECT count(*) AS count,md5(coalesce(string_agg(md5(to_jsonb(r)::text), '''' ORDER BY md5(to_jsonb(r)::text)), '''')) AS fingerprint FROM %I.%I r WHERE %I IN (SELECT id FROM food_item_serving_options WHERE food_item_id IN (__SELECTED_IDS__))',
 nspname,relname,attname),false,true,'') ELSE NULL END AS proof FROM refs
)
SELECT jsonb_build_object('kind','metrics','values',jsonb_build_object(
 'optionDependencies',coalesce((SELECT jsonb_agg(jsonb_build_object('constraint',conname,'schema',nspname,'table',relname,
 'column',attname,'supported',supported,'count',(xpath('/row/count/text()',proof))[1]::text,
 'fingerprint',(xpath('/row/fingerprint/text()',proof))[1]::text) ORDER BY nspname,relname,conname) FROM measured),'[]'::jsonb),
 'optionLinks',coalesce((SELECT jsonb_agg(jsonb_build_object('id',o.id,'foodId',o.food_item_id,
 'logs',(SELECT count(*) FROM food_logs l WHERE l.serving_option_id=o.id)) ORDER BY o.id)
 FROM food_item_serving_options o WHERE o.food_item_id IN (__SELECTED_IDS__)),'[]'::jsonb)));
