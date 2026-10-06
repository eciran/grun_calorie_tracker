param(
    [string]$Container = "grun-postgres",
    [string]$EvidencePath = ".\outputs\aws-catalog-readonly-audit\liquid-safety-queues-20261003\db-package\all-6759-decisions.csv",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review"
)

$ErrorActionPreference = "Stop"

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

$resolvedEvidence = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $EvidencePath))
if (-not (Test-Path $resolvedEvidence)) {
    throw "Liquid evidence package not found: $resolvedEvidence"
}

$query = @"
WITH products_with_options AS (
    SELECT DISTINCT food_item_id FROM food_item_serving_options
)
SELECT fi.id AS product_id,
       coalesce(fi.normalized_barcode, fi.barcode, '') AS barcode,
       coalesce(nullif(fi.display_name, ''), fi.name) AS product_name,
       coalesce(fi.brand, '') AS brand,
       fi.catalog_type,
       fi.data_source,
       coalesce(fi.source_key, '') AS source_key,
       fi.market_region,
       fi.nutrition_reference_unit,
       coalesce(fi.serving_unit, '') AS serving_unit
FROM food_items fi
LEFT JOIN products_with_options existing ON existing.food_item_id = fi.id
WHERE fi.publication_status = 'PUBLISHED'
  AND coalesce(fi.is_custom, false) = false
  AND existing.food_item_id IS NULL
ORDER BY fi.id
"@

$dbCsv = docker exec -e PGPASSWORD=$env:POSTGRES_PASSWORD $Container `
    psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 --csv -c $query
if ($LASTEXITCODE -ne 0) {
    throw "Missing-serving product query failed."
}

$missingProducts = @($dbCsv | ConvertFrom-Csv)
$evidenceRows = @(Import-Csv -LiteralPath $resolvedEvidence)
$evidenceByBarcode = @{}
foreach ($evidence in $evidenceRows) {
    $barcode = [string]$evidence.barcode
    if ($barcode -and -not $evidenceByBarcode.ContainsKey($barcode)) {
        $evidenceByBarcode[$barcode] = $evidence
    }
}

$liquidIdentityPattern = '(?i)(^|[^a-z])(milk|juice|coffee|water|drink|beverage|smoothie|shake|soda|cola|lemonade|kombucha|beer|ale|cider|wine|vodka|rum|gin|whisky|whiskey|tonic|ayran|kefir|syrup)([^a-z]|$)'
$dryPreparationPattern = '(?i)(powder|granules|tea bag|tea bags|leaves|biscotti|biscuit|cookie|cracker|chips|crisps|bread|cake|pasta|chicken|steak|chocolate|cereal|mix|oats|porridge|chickpeas|beans|lentils|tomatoes|bacon|tuna|segments|dairy milk|smooth milk|(^| )(in|with|added) .*water|boiled.*water|cooked.*water|in .*juice)'
$strongSolidPattern = '(?i)(^|[^a-z])(biscotti|biscuit|cookie|cracker|chips|bread|cake|pasta|chicken|steak)([^a-z]|$)'

$joined = foreach ($product in $missingProducts) {
    $evidence = if ($product.barcode) { $evidenceByBarcode[[string]$product.barcode] } else { $null }
    $name = [string]$product.product_name
    $hasLiquidIdentity = $name -match $liquidIdentityPattern -and $name -notmatch $dryPreparationPattern
    $hasStrongSolidConflict = $name -match $strongSolidPattern -and -not $hasLiquidIdentity
    $queue = if (-not $evidence) {
        if ($product.serving_unit -eq "ml" -or $hasLiquidIdentity) {
            "POTENTIAL_LIQUID_REQUIRES_SOURCE_FETCH"
        } else {
            "NOT_IN_PRIOR_LIQUID_AUDIT"
        }
    } elseif ($evidence.decision -eq "ML_SOURCE_SUPPORTED" -and $evidence.dbAction -eq "APPLY_PER_100ML_AND_100ML_SERVING" -and $hasStrongSolidConflict) {
        "CONFLICTING_PRODUCT_IDENTITY_REVIEW"
    } elseif ($evidence.decision -eq "ML_SOURCE_SUPPORTED" -and $evidence.dbAction -eq "APPLY_PER_100ML_AND_100ML_SERVING") {
        "SAFE_ML_SOURCE_SUPPORTED"
    } elseif ($evidence.decision -eq "MASS_VOLUME_BRIDGE_REQUIRED") {
        "KEEP_GRAM_NEEDS_DENSITY"
    } else {
        "SOURCE_BASIS_REVIEW"
    }
    [pscustomobject]@{
        product_id = $product.product_id
        barcode = $product.barcode
        product_name = $product.product_name
        brand = $product.brand
        catalog_type = $product.catalog_type
        data_source = $product.data_source
        source_key = $product.source_key
        market_region = $product.market_region
        current_reference_unit = $product.nutrition_reference_unit
        current_serving_unit = $product.serving_unit
        liquid_queue = $queue
        evidence_decision = if ($evidence) { $evidence.decision } else { "" }
        evidence_status = if ($evidence) { $evidence.evidenceStatus } else { "" }
        product_quantity_unit = if ($evidence) { $evidence.productQuantityUnit } else { "" }
        source_serving_unit = if ($evidence) { $evidence.servingQuantityUnit } else { "" }
        source_quantity = if ($evidence) { $evidence.quantity } else { "" }
        source_categories = if ($evidence) { $evidence.categories } else { "" }
    }
}

$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
$workspace = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not $resolvedOutput.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output directory must stay inside the workspace."
}
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

$joined | Export-Csv -LiteralPath (Join-Path $resolvedOutput "all-missing-serving-liquid-decisions.csv") -NoTypeInformation -Encoding utf8
foreach ($queue in @("SAFE_ML_SOURCE_SUPPORTED", "CONFLICTING_PRODUCT_IDENTITY_REVIEW", "KEEP_GRAM_NEEDS_DENSITY", "SOURCE_BASIS_REVIEW", "POTENTIAL_LIQUID_REQUIRES_SOURCE_FETCH", "NOT_IN_PRIOR_LIQUID_AUDIT")) {
    $joined | Where-Object { $_.liquid_queue -eq $queue } |
        Export-Csv -LiteralPath (Join-Path $resolvedOutput "$($queue.ToLowerInvariant()).csv") -NoTypeInformation -Encoding utf8
}

$summary = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    missingServingProducts = $missingProducts.Count
    evidenceRows = $evidenceRows.Count
    queues = @($joined | Group-Object liquid_queue | Sort-Object Name | ForEach-Object {
        [ordered]@{ queue = $_.Name; count = $_.Count }
    })
    safetyRule = "Only SAFE_ML_SOURCE_SUPPORTED may receive PER_100ML plus a verified 100 ml option. Other queues remain unchanged."
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resolvedOutput "summary.json") -Encoding utf8
$summary | ConvertTo-Json -Depth 5
