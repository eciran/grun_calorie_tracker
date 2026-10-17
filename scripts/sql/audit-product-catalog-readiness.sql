-- Product-only diagnostic. Run with psql -X -qAt -v ON_ERROR_STOP=1 -f <file>.
-- Results are candidate counts, not automatic correction approvals.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
SET LOCAL lock_timeout = '3s';
SET LOCAL idle_in_transaction_session_timeout = '30s';

SELECT json_build_object('section','identity','measuredAt',clock_timestamp(),
  'schemaVersion',(SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1),
  'transactionReadOnly',current_setting('transaction_read_only'));

SELECT json_build_object('section','inventory','rows',count(*),
  'nonCustom',count(*) FILTER (WHERE NOT coalesce(is_custom,false)),
  'publishedNonCustom',count(*) FILTER (WHERE NOT coalesce(is_custom,false) AND publication_status='PUBLISHED'),
  'missingCore',count(*) FILTER (WHERE calories IS NULL OR protein IS NULL OR fat IS NULL OR carbs IS NULL),
  'negativeCore',count(*) FILTER (WHERE calories<0 OR protein<0 OR fat<0 OR carbs<0),
  'nonFiniteCore',count(*) FILTER (WHERE calories::text IN ('NaN','Infinity','-Infinity')
    OR protein::text IN ('NaN','Infinity','-Infinity') OR fat::text IN ('NaN','Infinity','-Infinity')
    OR carbs::text IN ('NaN','Infinity','-Infinity')),
  'missingReferenceUnit',count(*) FILTER (WHERE nutrition_reference_unit IS NULL),
  'per100g',count(*) FILTER (WHERE nutrition_reference_unit='PER_100G'),
  'per100ml',count(*) FILTER (WHERE nutrition_reference_unit='PER_100ML'),
  'missingDisplayName',count(*) FILTER (WHERE nullif(btrim(display_name),'') IS NULL),
  'technicalCommaNameCandidates',count(*) FILTER (WHERE coalesce(display_name,name) LIKE '%,%,%'),
  'missingPreparationState',count(*) FILTER (WHERE preparation_state IS NULL),
  'missingBrandOnBranded',count(*) FILTER (WHERE catalog_type='BRANDED_PRODUCT' AND nullif(btrim(brand),'') IS NULL),
  'missingBarcodeOnBranded',count(*) FILTER (WHERE catalog_type='BRANDED_PRODUCT' AND nullif(btrim(barcode),'') IS NULL),
  'publishedIncompleteCore',count(*) FILTER (WHERE publication_status='PUBLISHED'
    AND (calories IS NULL OR protein IS NULL OR fat IS NULL OR carbs IS NULL)),
  'publishedNegativeCore',count(*) FILTER (WHERE publication_status='PUBLISHED'
    AND (calories<0 OR protein<0 OR fat<0 OR carbs<0)))
FROM food_items WHERE NOT coalesce(is_custom,false);

SELECT json_build_object('section','catalogTypes','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT catalog_type,data_source,publication_status,count(*) AS rows
  FROM food_items WHERE NOT coalesce(is_custom,false)
  GROUP BY catalog_type,data_source,publication_status ORDER BY count(*) DESC) t;

WITH barcodes AS (
  SELECT id,coalesce(nullif(btrim(normalized_barcode),''),nullif(btrim(barcode),'')) AS code
  FROM food_items WHERE NOT coalesce(is_custom,false) AND catalog_type='BRANDED_PRODUCT'
), valid_format AS (
  SELECT id,code FROM barcodes WHERE code ~ '^(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})$'
), checked AS (
  SELECT id,code, (10 - sum(substr(code,length(code)-pos,1)::int *
    CASE WHEN pos%2=1 THEN 3 ELSE 1 END)%10)%10 = right(code,1)::int AS valid_checksum
  FROM valid_format CROSS JOIN LATERAL generate_series(1,length(code)-1) pos GROUP BY id,code
)
SELECT json_build_object('section','gtin','brandedRows',(SELECT count(*) FROM barcodes),
  'missing',(SELECT count(*) FROM barcodes WHERE code IS NULL),
  'invalidFormat',(SELECT count(*) FROM barcodes WHERE code IS NOT NULL
    AND code !~ '^(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})$'),
  'validChecksum',count(*) FILTER (WHERE valid_checksum),
  'invalidChecksum',count(*) FILTER (WHERE NOT valid_checksum)) FROM checked;

WITH groups AS (
  SELECT 'SOURCE_KEY' AS kind,source_key AS key,count(*) AS rows
  FROM food_items WHERE NOT coalesce(is_custom,false) AND nullif(btrim(source_key),'') IS NOT NULL
  GROUP BY source_key HAVING count(*)>1
  UNION ALL
  SELECT 'NORMALIZED_BARCODE',normalized_barcode,count(*)
  FROM food_items WHERE NOT coalesce(is_custom,false) AND nullif(btrim(normalized_barcode),'') IS NOT NULL
  GROUP BY normalized_barcode HAVING count(*)>1
  UNION ALL
  SELECT 'CANONICAL_KEY_CANDIDATE',canonical_food_key,count(*)
  FROM food_items WHERE NOT coalesce(is_custom,false) AND nullif(btrim(canonical_food_key),'') IS NOT NULL
  GROUP BY canonical_food_key HAVING count(*)>1
)
SELECT json_build_object('section','identityGroups','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT kind,count(*) AS groups,sum(rows) AS candidate_rows FROM groups GROUP BY kind) t;

WITH coverage AS (
  SELECT food_item_id,count(*) FILTER (WHERE quality_status='VERIFIED' AND user_visible
    AND quantity>0 AND (gram_weight>0 OR ml_volume>0)) AS verified_visible_options,
    bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0 AND gram_weight>0) AS mass_option,
    bool_or(quality_status='VERIFIED' AND user_visible AND quantity>0 AND ml_volume>0) AS volume_option
  FROM food_item_serving_options GROUP BY food_item_id
)
SELECT json_build_object('section','servings','publishedRows',count(*),
  'withoutVerifiedVisibleOption',count(*) FILTER (WHERE coalesce(c.verified_visible_options,0)=0),
  'oneVerifiedVisibleOption',count(*) FILTER (WHERE c.verified_visible_options=1),
  'multipleVerifiedVisibleOptions',count(*) FILTER (WHERE c.verified_visible_options>1),
  'per100gWithoutMassOption',count(*) FILTER (WHERE f.nutrition_reference_unit='PER_100G' AND NOT coalesce(c.mass_option,false)),
  'per100mlWithoutVolumeOption',count(*) FILTER (WHERE f.nutrition_reference_unit='PER_100ML' AND NOT coalesce(c.volume_option,false)),
  'per100gWithVolumeButNoMass',count(*) FILTER (WHERE f.nutrition_reference_unit='PER_100G'
    AND coalesce(c.volume_option,false) AND NOT coalesce(c.mass_option,false)))
FROM food_items f LEFT JOIN coverage c ON c.food_item_id=f.id
WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED';

SELECT json_build_object('section','localization','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT f.catalog_type,count(*) AS published,
  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_item_localizations l WHERE l.food_item_id=f.id AND l.language='TR' AND l.active)) AS with_tr,
  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_item_localizations l WHERE l.food_item_id=f.id AND l.language='EN' AND l.active)) AS with_en,
  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_item_search_aliases a WHERE a.food_item_id=f.id)) AS with_alias,
  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_product_source_evidence e WHERE e.food_item_id=f.id)) AS with_numeric_source_evidence
  FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED'
  GROUP BY f.catalog_type ORDER BY count(*) DESC) t;

SELECT json_build_object('section','categoryCoverage','publishedRows',count(*),
  'withSourceCategoryTags',count(*) FILTER (WHERE EXISTS
    (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id)),
  'beverageTagMassBasisCandidates',count(*) FILTER (WHERE f.nutrition_reference_unit='PER_100G'
    AND EXISTS (SELECT 1 FROM food_item_source_categories c WHERE c.food_item_id=f.id
      AND c.category_tag IN ('en:beverages','en:non-alcoholic-beverages','en:alcoholic-beverages','en:milks','en:plant-based-milks'))))
FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED';

SELECT json_build_object('section','qualitySuggestions','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT status,source,count(*) AS rows FROM product_quality_suggestions GROUP BY status,source) t;

SELECT json_build_object('section','qualityRuns','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT trigger_type,status,count(*) AS rows,max(started_at) AS latest
  FROM product_quality_scan_runs GROUP BY trigger_type,status) t;

SELECT json_build_object('section','publishedCoreFailureSamples','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT id,name,display_name,catalog_type,data_source,nutrition_reference_unit,calories,protein,fat,carbs
  FROM food_items WHERE NOT coalesce(is_custom,false) AND publication_status='PUBLISHED'
    AND (calories IS NULL OR protein IS NULL OR fat IS NULL OR carbs IS NULL
      OR calories<0 OR protein<0 OR fat<0 OR carbs<0) ORDER BY id LIMIT 15) t;

SELECT json_build_object('section','servingsByType','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT f.catalog_type,count(*) AS published,
  count(*) FILTER (WHERE f.serving_size_grams>0) AS legacy_positive_serving_grams,
  count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_item_serving_options o
    WHERE o.food_item_id=f.id AND o.quality_status='VERIFIED' AND o.user_visible
      AND o.quantity>0 AND (o.gram_weight>0 OR o.ml_volume>0))) AS with_structured_option
  FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED'
  GROUP BY f.catalog_type) t;

SELECT json_build_object('section','volumeEvidence','volumeRows',count(*),
  'withVolumeCoreEvidence',count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_product_source_evidence e
    WHERE e.food_item_id=f.id AND e.field_name IN ('CALORIES','PROTEIN','FAT','CARBS') AND e.basis='PER_100_ML')),
  'withMassCoreEvidence',count(*) FILTER (WHERE EXISTS (SELECT 1 FROM food_product_source_evidence e
    WHERE e.food_item_id=f.id AND e.field_name IN ('CALORIES','PROTEIN','FAT','CARBS') AND e.basis='PER_100_G')))
FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.nutrition_reference_unit='PER_100ML';

SELECT json_build_object('section','canonicalCategoryCoverage','publishedRows',count(*),
  'withAnyCanonicalCategory',count(*) FILTER (WHERE EXISTS
    (SELECT 1 FROM food_item_categories c WHERE c.food_item_id=f.id)),
  'withPrimaryCanonicalCategory',count(*) FILTER (WHERE EXISTS
    (SELECT 1 FROM food_item_categories c WHERE c.food_item_id=f.id AND c.primary_category)),
  'brandedWithoutCanonicalBrand',count(*) FILTER (WHERE f.catalog_type='BRANDED_PRODUCT' AND f.brand_id IS NULL))
FROM food_items f WHERE NOT coalesce(f.is_custom,false) AND f.publication_status='PUBLISHED';

SELECT json_build_object('section','micronutrients','rows',count(*),
  'withoutAnySelectedMicronutrient',count(*) FILTER (WHERE sodium IS NULL AND potassium IS NULL
    AND calcium IS NULL AND iron IS NULL AND magnesium IS NULL AND zinc IS NULL AND vitamin_a IS NULL
    AND vitamin_c IS NULL AND vitamin_d IS NULL AND vitamin_e IS NULL AND vitamin_b12 IS NULL),
  'withoutAnySelectedVitamin',count(*) FILTER (WHERE vitamin_a IS NULL AND vitamin_c IS NULL
    AND vitamin_d IS NULL AND vitamin_e IS NULL AND vitamin_b12 IS NULL))
FROM food_items WHERE NOT coalesce(is_custom,false) AND publication_status='PUBLISHED';

SELECT json_build_object('section','brandedCollapse','activeGroups',
  (SELECT count(*) FROM food_branded_duplicate_search_collapses WHERE active),
  'suppressedProducts',(SELECT count(DISTINCT m.suppressed_food_item_id)
    FROM food_branded_duplicate_search_collapse_members m
    JOIN food_branded_duplicate_search_collapses c ON c.id=m.collapse_id WHERE c.active));

SELECT json_build_object('section','misclassificationCandidates','values',coalesce(json_agg(t),'[]'::json))
FROM (SELECT id,name,catalog_type,data_source,source_key,barcode
  FROM food_items WHERE NOT coalesce(is_custom,false) AND catalog_type='BRANDED_PRODUCT'
    AND data_source='USDA_FOODDATA' AND nullif(btrim(barcode),'') IS NULL ORDER BY id LIMIT 12) t;

-- Verify the checksum algorithm against known valid, invalid and non-GTIN fixtures.
WITH fixtures(code,expected) AS (VALUES
  ('3017620422003',true),('3017620422004',false),('01234565',true),('01234566',false),
  ('036000291452',true),('036000291453',false),('00012345600012',true),
  ('not-a-gtin',false),('123',false)),
checked AS (SELECT code,expected,
  CASE WHEN code ~ '^(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})$' THEN
    (SELECT (10-sum(substr(code,length(code)-pos,1)::int * CASE WHEN pos%2=1 THEN 3 ELSE 1 END)%10)%10
      = right(code,1)::int FROM generate_series(1,length(code)-1) pos)
  ELSE false END AS actual FROM fixtures)
SELECT json_build_object('section','auditSelfTest','cases',count(*),
  'passed',count(*) FILTER (WHERE actual=expected),'allPassed',bool_and(actual=expected)) FROM checked;

COMMIT;
