param(
    [string]$ReconciliationPath = ".\outputs\serving-option-candidates\liquid-review\nutrition-reconciliation\all-reconciliations.csv",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review\ml-apply-package"
)

$ErrorActionPreference = 'Stop'
# Retain the historical generator for auditability, but never issue new unsafe SQL.
throw 'Legacy ml SQL generation is disabled: numeric similarity is not a unit/identity proof. Use build-r5-serving-review.py; live changes require the R8 backup, exact before-image, source and rollback gates.'
$workspace = [IO.Path]::GetFullPath((Get-Location).Path)
$input = [IO.Path]::GetFullPath((Join-Path $workspace $ReconciliationPath))
$output = [IO.Path]::GetFullPath((Join-Path $workspace $OutputDirectory))
if (-not $input.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase) -or
    -not $output.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Input and output paths must stay inside the workspace.'
}

$safe = @(Import-Csv -LiteralPath $input | Where-Object {
    $_.decision -eq 'LOCAL_VALUES_MATCH_SOURCE_100_UNIT' -and
    $_.source_nutrition_data_per -eq '100ml' -and
    $_.barcode -match '^\d{8,14}$'
})
if ($safe.Count -eq 0) { throw 'No safe PER_100ML candidates found.' }
if (@($safe.barcode | Sort-Object -Unique).Count -ne $safe.Count) { throw 'Duplicate barcodes in safe candidate set.' }

New-Item -ItemType Directory -Force -Path $output | Out-Null
$manifest = $safe | Sort-Object barcode | Select-Object product_id,barcode,product_name,brand,
    local_reference_unit,local_serving_unit,source_nutrition_data_per,local_calories,source_calories_100,
    local_protein,source_protein_100,local_carbs,source_carbs_100,local_fat,source_fat_100,
    serving_size,product_quantity_unit,serving_quantity_unit
$manifest | Export-Csv -LiteralPath (Join-Path $output 'manifest.csv') -NoTypeInformation -Encoding utf8

function Sql-Literal([string]$value) { return "'" + $value.Replace("'", "''") + "'" }
$barcodeValues = ($manifest | ForEach-Object { '(' + (Sql-Literal $_.barcode) + ')' }) -join ",`n        "
$rollbackValues = ($manifest | ForEach-Object {
    '(' + (Sql-Literal $_.barcode) + ',' + (Sql-Literal $_.local_serving_unit) + ')'
}) -join ",`n        "
$expectedCount = $manifest.Count

$preflight = @"
-- Read-only gate. Must return exactly $expectedCount eligible rows and zero blockers.
WITH expected(barcode) AS (
    VALUES
        $barcodeValues
), matched AS (
    SELECT e.barcode, f.id, f.name, f.nutrition_reference_unit,
           count(o.id) AS existing_options
    FROM expected e
    LEFT JOIN food_items f ON coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode
    LEFT JOIN food_item_serving_options o ON o.food_item_id = f.id
    GROUP BY e.barcode, f.id, f.name, f.nutrition_reference_unit
)
SELECT count(*) AS expected_count,
       count(id) AS matched_count,
       count(*) FILTER (WHERE id IS NULL) AS missing_products,
       count(*) FILTER (WHERE nutrition_reference_unit <> 'PER_100G') AS unexpected_reference_unit,
       count(*) FILTER (WHERE existing_options <> 0) AS products_with_existing_options
FROM matched;
"@
$preflight | Set-Content -LiteralPath (Join-Path $output '01-preflight.sql') -Encoding utf8

$apply = @"
BEGIN;
CREATE TEMP TABLE expected_liquid_ml(barcode text PRIMARY KEY, old_serving_unit text NOT NULL) ON COMMIT DROP;
INSERT INTO expected_liquid_ml(barcode,old_serving_unit) VALUES
    $rollbackValues;

DO `$gate`$
DECLARE matched_count integer; blocker_count integer;
BEGIN
    SELECT count(f.id), count(*) FILTER (
        WHERE f.id IS NULL OR f.nutrition_reference_unit <> 'PER_100G'
           OR EXISTS (SELECT 1 FROM food_item_serving_options o WHERE o.food_item_id = f.id)
    ) INTO matched_count, blocker_count
    FROM expected_liquid_ml e
    LEFT JOIN food_items f ON coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode;
    IF matched_count <> $expectedCount OR blocker_count <> 0 THEN
        RAISE EXCEPTION 'Liquid ml exact-count gate failed: matched %, blockers %', matched_count, blocker_count;
    END IF;
END `$gate`$;

UPDATE food_items f
SET nutrition_reference_unit = 'PER_100ML', serving_unit = 'ml'
FROM expected_liquid_ml e
WHERE coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode;

INSERT INTO food_item_serving_options
    (food_item_id,label,unit_type,quantity,gram_weight,ml_volume,is_default,source,quality_status)
SELECT f.id,'100 ml','SERVING',1,NULL,100,TRUE,'OPEN_FOOD_FACTS','VERIFIED'
FROM expected_liquid_ml e
JOIN food_items f ON coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode;

DO `$verify`$
DECLARE updated_count integer; option_count integer;
BEGIN
    SELECT count(*) INTO updated_count FROM expected_liquid_ml e JOIN food_items f
      ON coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode
     AND f.nutrition_reference_unit = 'PER_100ML' AND f.serving_unit = 'ml';
    SELECT count(*) INTO option_count FROM expected_liquid_ml e JOIN food_items f
      ON coalesce(nullif(f.normalized_barcode, ''), f.barcode) = e.barcode
      JOIN food_item_serving_options o ON o.food_item_id=f.id
     AND o.label='100 ml' AND o.ml_volume=100 AND o.source='OPEN_FOOD_FACTS';
    IF updated_count <> $expectedCount OR option_count <> $expectedCount THEN
        RAISE EXCEPTION 'Liquid ml verification failed: products %, options %', updated_count, option_count;
    END IF;
END `$verify`$;
COMMIT;
"@
$apply | Set-Content -LiteralPath (Join-Path $output '02-apply.sql') -Encoding utf8

$rollback = @"
BEGIN;
CREATE TEMP TABLE expected_liquid_ml(barcode text PRIMARY KEY, old_serving_unit text NOT NULL) ON COMMIT DROP;
INSERT INTO expected_liquid_ml(barcode,old_serving_unit) VALUES
    $rollbackValues;
DELETE FROM food_item_serving_options o
USING food_items f, expected_liquid_ml e
WHERE o.food_item_id=f.id
  AND coalesce(nullif(f.normalized_barcode, ''), f.barcode)=e.barcode
  AND o.label='100 ml' AND o.unit_type='SERVING' AND o.ml_volume=100
  AND o.source='OPEN_FOOD_FACTS' AND o.quality_status='VERIFIED';
UPDATE food_items f SET nutrition_reference_unit='PER_100G', serving_unit=e.old_serving_unit
FROM expected_liquid_ml e
WHERE coalesce(nullif(f.normalized_barcode, ''), f.barcode)=e.barcode
  AND f.nutrition_reference_unit='PER_100ML' AND f.serving_unit='ml';
COMMIT;
"@
$rollback | Set-Content -LiteralPath (Join-Path $output '03-rollback.sql') -Encoding utf8

$summary = [ordered]@{
    generatedAt=[DateTime]::UtcNow.ToString('o'); applyReady=$expectedCount
    identity='barcode'; targetReferenceUnit='PER_100ML'; defaultServing='100 ml'
    safety='No database writes performed. Run preflight first; apply uses an exact-count transactional gate; rollback only targets the exact inserted option signature.'
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'summary.json') -Encoding utf8
$summary | ConvertTo-Json
