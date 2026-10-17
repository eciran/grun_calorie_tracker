"""Bounded SELECT-only current product/dependency review, not runtime approval."""
import json
from pathlib import Path


def build(selection):
    rows = [json.loads(line) for line in selection.read_text(encoding='utf-8').splitlines() if line.strip()]
    ids = [r['id'] for r in rows]
    if (not 1 <= len(ids) <= 300 or any(isinstance(i, bool) or not str(i).isdigit() or int(i) <= 0 for i in ids)
            or len({int(i) for i in ids}) != len(ids)):
        raise ValueError('Bounded unique positive IDs required')
    ids_sql = ','.join(str(i) for i in sorted(map(int, ids)))
    contract = json.loads(Path('src/main/resources/catalog/product-dependency-fk-contract-v310.json').read_text())
    paths = sorted(contract['requiredPaths'])
    if len(paths) != 31 or len(set(paths)) != 31: raise ValueError('Exact31 reviewed paths required')
    relations = []
    for path in paths:
        left, right = path.split('->')
        schema, table, column = left.split('.')
        if table in ('food_product_source_evidence', 'food_typed_nutrient_evidence'): continue
        if schema != 'public' or right not in ('food_items.id', 'food_item_serving_options.id'):
            raise ValueError('Unsupported reviewed dependency')
        if not all(p.replace('_', '').isalnum() and p.isascii() and p[0].isalpha() for p in (schema, table, column)):
            raise ValueError('Unsafe reviewed identifier')
        predicate = f'd."{column}"=f.id' if right == 'food_items.id' else f'd."{column}" IN (SELECT id FROM food_item_serving_options WHERE food_item_id=f.id)'
        relations.append(f"""(SELECT jsonb_build_object('path','{path}','rowsExamined',count(*),
            'rowHashes',coalesce(jsonb_agg(row_hash ORDER BY row_hash),'[]'::jsonb)) FROM
            (SELECT encode(sha256(convert_to(to_jsonb(d)::text,'UTF8')),'hex') AS row_hash
             FROM \"{schema}\".\"{table}\" d WHERE {predicate} LIMIT 4097) bounded)""")
    expected = "ARRAY[" + ','.join("'" + p + "'" for p in paths) + "]::text[]"
    def aggregate(table, order, predicate='c.food_item_id=f.id'):
        return f"(SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY c.{order}),'[]'::jsonb) FROM (SELECT c.* FROM {table} c WHERE {predicate} ORDER BY c.{order} LIMIT 4097) c)"
    evidence = aggregate('food_product_source_evidence', 'id')
    return f"""BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='90s'; SET LOCAL lock_timeout='3s';
SELECT json_build_object('kind','start','time',now(),'readonly',current_setting('transaction_read_only'),
 'schema',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1),
 'count',(SELECT count(*) FROM food_items WHERE id IN ({ids_sql}) AND NOT coalesce(is_custom,false)));
WITH layout AS MATERIALIZED (
 SELECT n.nspname||'.'||t.relname||'.'||a.attname||'->'||target.relname||'.'||ta.attname AS path,
 c.conname,cardinality(c.conkey) AS key_columns,cardinality(c.confkey) AS target_columns,
 c.convalidated,t.relkind::text,format_type(a.atttypid,a.atttypmod) AS column_type,
 c.confdeltype::text AS delete_action,c.confupdtype::text AS update_action,c.condeferrable,c.condeferred
 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
 JOIN pg_class target ON target.oid=c.confrelid
 LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=c.conkey[1]
 LEFT JOIN pg_attribute ta ON ta.attrelid=target.oid AND ta.attnum=c.confkey[1]
 WHERE c.contype='f' AND c.confrelid IN ('public.food_items'::regclass,'public.food_item_serving_options'::regclass)
), chosen AS MATERIALIZED (SELECT f.* FROM food_items f WHERE f.id IN ({ids_sql}) AND NOT coalesce(f.is_custom,false))
SELECT json_build_object('kind','row','row',json_build_object(
 'id',f.id,'food',to_jsonb(f),
 'categories',{aggregate('food_item_categories', 'category_id')},
 'markets',{aggregate('food_item_market_regions', 'market_region')},
 'source_categories',{aggregate('food_item_source_categories', 'category_tag')},
 'localizations',{aggregate('food_item_localizations', 'id')},
 'aliases',{aggregate('food_item_search_aliases', 'id')},
 'options',{aggregate('food_item_serving_options', 'id')},
 'option_localizations',{aggregate('food_item_serving_option_localizations', 'id', 'c.serving_option_id IN (SELECT id FROM food_item_serving_options WHERE food_item_id=f.id)')},
 'evidence',{evidence},'metadataRowLimit',4097,
 'dependencies',jsonb_build_object('scope','R5_CURRENT_FK_REVIEW_NOT_RUNTIME_V2_APPROVAL',
 'layout',coalesce((SELECT jsonb_agg(to_jsonb(l) ORDER BY path) FROM layout l),'[]'::jsonb),
 'relations',r.relations,'rowsExamined',(SELECT sum((v->>'rowsExamined')::int) FROM jsonb_array_elements(r.relations) v),
 'complete',(SELECT coalesce(array_agg(path ORDER BY path COLLATE "C")={expected} AND bool_and(key_columns=1 AND target_columns=1 AND convalidated AND relkind IN ('r','p') AND column_type='bigint'),false) FROM layout)
     AND (SELECT sum((v->>'rowsExamined')::int)<=4096 FROM jsonb_array_elements(r.relations) v),
 'sourceEvidenceComplete',jsonb_array_length({evidence})<=4096,
 'runtimeApprovalFingerprint',NULL)))
FROM chosen f CROSS JOIN LATERAL (SELECT jsonb_build_array({','.join(relations)}) AS relations) r ORDER BY f.id;
SELECT json_build_object('kind','end','count',(SELECT count(*) FROM food_items WHERE id IN ({ids_sql}) AND NOT coalesce(is_custom,false)));
ROLLBACK;
"""
