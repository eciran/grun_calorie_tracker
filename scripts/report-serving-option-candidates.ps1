param(
    [string]$Container = "grun-postgres",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates",
    [int]$Limit = 100000
)

$ErrorActionPreference = "Stop"

if ($Limit -lt 1 -or $Limit -gt 100000) {
    throw "Limit must be between 1 and 100000."
}

$envFile = Join-Path (Get-Location) ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

foreach ($required in @("POSTGRES_USER", "POSTGRES_PASSWORD", "POSTGRES_DB")) {
    if (-not [Environment]::GetEnvironmentVariable($required, "Process")) {
        throw "Missing required environment variable: $required"
    }
}

$query = @"
WITH option_counts AS (
    SELECT food_item_id,
           count(*) AS current_options,
           count(*) FILTER (WHERE quality_status = 'VERIFIED') AS verified_options,
           string_agg(DISTINCT unit_type, '|' ORDER BY unit_type) AS existing_units
    FROM food_item_serving_options
    GROUP BY food_item_id
), candidates AS (
    SELECT fi.id AS product_id,
           coalesce(nullif(fi.display_name, ''), fi.name) AS product_name,
           coalesce(fi.brand, '') AS brand,
           fi.catalog_type,
           fi.data_source,
           coalesce(fi.source_key, '') AS source_key,
           fi.market_region,
           fi.preparation_state,
           fi.nutrition_reference_unit,
           coalesce(oc.current_options, 0) AS current_options,
           coalesce(oc.verified_options, 0) AS verified_options,
           coalesce(oc.existing_units, '') AS existing_units,
           CASE
             WHEN fi.nutrition_reference_unit = 'PER_100ML' THEN 'DRINKWARE_VOLUME'
             WHEN lower(coalesce(fi.display_name, fi.name)) ~ '(^|[^a-z])(soup|stew|curry)([^a-z]|$)' THEN 'BOWL_OR_PORTION'
             WHEN lower(trim(coalesce(fi.display_name, fi.name))) ~ '^(raw |fresh |cooked |boiled |baked )?(banana|apple|orange|mandarin|pear|peach|avocado|potato|egg|kiwi|plum|tomato|bagel|muffin|roll)s?$' THEN 'COUNTABLE_SIZE'
             WHEN lower(trim(coalesce(fi.display_name, fi.name))) ~ '^(white |wholemeal |whole wheat |brown |sliced |mature |cheddar |cooked |smoked )*(bread|cheese|ham|cake|loaf)$' THEN 'SLICE_OR_PIECE'
             WHEN lower(coalesce(fi.display_name, fi.name)) ~ '(^|[^a-z])(yogurt|yoghurt|butter|spread|sauce|jam|honey|oats?|cereal)([^a-z]|$)' THEN 'SPOON_OR_CUP'
             WHEN fi.catalog_type IN ('STANDARD_PREPARED_ITEM', 'LOCAL_DISH') THEN 'PORTION_OR_PLATE'
             WHEN fi.catalog_type = 'BRANDED_PRODUCT' THEN 'PACKAGE_EVIDENCE'
             ELSE 'SOURCE_PORTION_LOOKUP'
           END AS suggested_profile,
           CASE
             WHEN coalesce(oc.current_options, 0) = 0 THEN 'BASE_OPTION_MISSING'
             WHEN coalesce(oc.current_options, 0) = 1 AND coalesce(oc.verified_options, 0) = 0 THEN 'REVIEW_THEN_EXPAND'
             WHEN fi.data_source IN ('USDA_FOODDATA', 'COFID') AND fi.catalog_type <> 'BRANDED_PRODUCT' THEN 'SOURCE_PROFILE_LOOKUP'
             WHEN fi.catalog_type = 'BRANDED_PRODUCT' THEN 'PRODUCT_SPECIFIC_EVIDENCE'
             ELSE 'CURATOR_REVIEW'
           END AS review_lane,
           CASE
             WHEN fi.data_source IN ('USDA_FOODDATA', 'COFID') AND nullif(fi.source_key, '') IS NOT NULL THEN 1
             WHEN fi.catalog_type IN ('GENERIC_INGREDIENT', 'STANDARD_PREPARED_ITEM', 'LOCAL_DISH') THEN 2
             ELSE 3
           END AS priority
    FROM food_items fi
    LEFT JOIN option_counts oc ON oc.food_item_id = fi.id
    WHERE fi.publication_status = 'PUBLISHED'
      AND coalesce(fi.is_custom, false) = false
      AND coalesce(oc.current_options, 0) <= 1
)
SELECT product_id, product_name, brand, catalog_type, data_source, source_key,
       market_region, preparation_state, nutrition_reference_unit,
       current_options, verified_options, existing_units, suggested_profile, review_lane, priority
FROM candidates
ORDER BY CASE WHEN current_options = 1 THEN 0 ELSE 1 END, priority, review_lane, product_name, product_id
LIMIT $Limit
"@

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
$workspace = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not $resolvedOutput.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must stay inside the workspace."
}
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

$csv = docker exec -e PGPASSWORD=$env:POSTGRES_PASSWORD $Container `
    psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 `
    --csv -c $query
if ($LASTEXITCODE -ne 0) {
    throw "Candidate query failed."
}

$csvPath = Join-Path $resolvedOutput "serving-option-candidates.csv"
$csv | Set-Content -LiteralPath $csvPath -Encoding utf8
$rows = @($csv | ConvertFrom-Csv)
$expandableRows = @($rows | Where-Object { [int]$_.current_options -eq 1 })
$missingRows = @($rows | Where-Object { [int]$_.current_options -eq 0 })
$structuredExpansionRows = @($expandableRows | Where-Object {
    $_.suggested_profile -notin @("SOURCE_PORTION_LOOKUP", "PACKAGE_EVIDENCE")
})
$expandableRows | Export-Csv -LiteralPath (Join-Path $resolvedOutput "expand-existing-option-candidates.csv") -NoTypeInformation -Encoding utf8
$missingRows | Export-Csv -LiteralPath (Join-Path $resolvedOutput "missing-base-option-candidates.csv") -NoTypeInformation -Encoding utf8
$structuredExpansionRows | Export-Csv -LiteralPath (Join-Path $resolvedOutput "priority-structured-expansion-shortlist.csv") -NoTypeInformation -Encoding utf8

$summary = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    candidateCount = $rows.Count
    expandExistingOptionCount = $expandableRows.Count
    structuredExpansionShortlistCount = $structuredExpansionRows.Count
    missingBaseOptionCount = $missingRows.Count
    byReviewLane = @($rows | Group-Object review_lane | Sort-Object Name | ForEach-Object {
        [ordered]@{ lane = $_.Name; count = $_.Count }
    })
    bySuggestedProfile = @($rows | Group-Object suggested_profile | Sort-Object Count -Descending | ForEach-Object {
        [ordered]@{ profile = $_.Name; count = $_.Count }
    })
    rules = @(
        "This report is read-only and does not approve or insert serving options.",
        "SOURCE_PROFILE_LOOKUP requires exact source-key portion evidence.",
        "PRODUCT_SPECIFIC_EVIDENCE requires barcode/package or manufacturer evidence.",
        "Half/double arithmetic variants must be handled by quantity scaling, not extra rows."
    )
}
$summaryPath = Join-Path $resolvedOutput "summary.json"
$summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $summaryPath -Encoding utf8

$summary | ConvertTo-Json -Depth 6
