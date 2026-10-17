param(
    [string]$CandidatePath = ".\outputs\serving-option-candidates\liquid-review\potential_liquid_requires_source_fetch.csv",
    [string]$EvidenceDirectory = ".\outputs\serving-option-candidates\liquid-review\live-off-evidence\items",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review\live-off-analysis",
    [string]$PriorSafePath = ".\outputs\serving-option-candidates\liquid-review\safe_ml_source_supported.csv"
)

$ErrorActionPreference = "Stop"
$candidates = @(Import-Csv -LiteralPath $CandidatePath)
$candidateByBarcode = @{}
foreach ($row in $candidates) {
    if ($row.barcode) { $candidateByBarcode[[string]$row.barcode] = $row }
}

$liquidCategories = @(
    'en:beverages', 'en:waters', 'en:juices-and-nectars', 'en:fruit-juices',
    'en:soft-drinks', 'en:carbonated-drinks', 'en:energy-drinks', 'en:sports-drinks',
    'en:milk-drinks', 'en:dairy-drinks', 'en:milks', 'en:fermented-drinks',
    'en:fermented-milk-drinks', 'en:plant-based-beverages', 'en:coffee-drinks',
    'en:tea-based-beverages', 'en:iced-teas', 'en:smoothies', 'en:alcoholic-beverages',
    'en:beers', 'en:wines', 'en:ciders'
)
$conflictCategories = @(
    'en:powders', 'en:drink-powders', 'en:beverage-preparations', 'en:concentrates',
    'en:ice-creams', 'en:frozen-desserts', 'en:pickles', 'en:tea-bags', 'en:coffee-beans'
)
$liquidIdentityPattern = '(?i)(^|[^a-z])(milk|juice|coffee|water|drink|beverage|smoothie|shake|soda|cola|lemonade|kombucha|beer|ale|cider|wine|vodka|rum|gin|whisky|whiskey|tonic|ayran|kefir|vinegar)([^a-z]|$)'
$strongSolidPattern = '(?i)(^|[^a-z])(biscotti|biscuit|cookie|cracker|chips|crisps|bread|cake|pasta|chicken|steak|chocolate bar|cereal bar|tuna|beans|lentils|powder|granules|tea bags?|coffee beans|infuse)([^a-z]|$)'

$results = foreach ($file in Get-ChildItem -LiteralPath $EvidenceDirectory -File -Filter '*.json') {
    $evidence = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $candidate = $candidateByBarcode[[string]$evidence.barcode]
    $product = $evidence.product
    $categories = @($product.categories_tags | ForEach-Object { ([string]$_).ToLowerInvariant() })
    $hasLiquidCategory = @($categories | Where-Object { $liquidCategories -contains $_ }).Count -gt 0
    $hasConflictCategory = @($categories | Where-Object { $conflictCategories -contains $_ }).Count -gt 0
    $sourceName = [string]$product.product_name
    $hasSolidNameConflict = $sourceName -match $strongSolidPattern
    $hasLiquidIdentity = $sourceName -match $liquidIdentityPattern
    $quantity = [string]$product.quantity
    $hasVolumeUnit = ([string]$product.product_quantity_unit).ToLowerInvariant() -eq 'ml' -or
        ([string]$product.serving_quantity_unit).ToLowerInvariant() -eq 'ml' -or
        $quantity -match '(?i)(^|\s|\d)(ml|cl|l|litre|liter)(\s|$)'

    $decision = if ($evidence.status -ne 'FETCHED') {
        $evidence.status
    } elseif ($hasConflictCategory -or $hasSolidNameConflict) {
        'FORM_CONFLICT_REVIEW'
    } elseif ($hasVolumeUnit -and ($hasLiquidCategory -or $hasLiquidIdentity)) {
        'ML_PHYSICAL_FORM_SOURCE_SUPPORTED'
    } elseif ($hasLiquidCategory -and $hasLiquidIdentity) {
        'ML_PHYSICAL_FORM_UNIT_MISSING'
    } elseif ($hasLiquidCategory) {
        'LIQUID_CATEGORY_UNIT_MISSING'
    } elseif ($hasVolumeUnit) {
        'VOLUME_UNIT_CATEGORY_MISSING'
    } else {
        'NOT_LIQUID_BY_SOURCE'
    }

    [pscustomobject]@{
        product_id = if ($candidate) { $candidate.product_id } else { $evidence.productId }
        barcode = $evidence.barcode
        local_name = if ($candidate) { $candidate.product_name } else { '' }
        source_name = $sourceName
        brand = if ($candidate) { $candidate.brand } else { [string]$product.brands }
        status = $evidence.status
        decision = $decision
        nutrition_data_per = [string]$product.nutrition_data_per
        product_quantity_unit = [string]$product.product_quantity_unit
        serving_quantity_unit = [string]$product.serving_quantity_unit
        serving_size = [string]$product.serving_size
        quantity = $quantity
        categories = $categories -join ','
        nutrition_reconciliation_required = $decision -eq 'ML_PHYSICAL_FORM_SOURCE_SUPPORTED' -and [string]$product.nutrition_data_per -ne '100ml'
    }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$results | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'all-live-decisions.csv') -NoTypeInformation -Encoding utf8
foreach ($decision in @($results.decision | Sort-Object -Unique)) {
    $safeName = $decision.ToLowerInvariant()
    $results | Where-Object decision -eq $decision |
        Export-Csv -LiteralPath (Join-Path $OutputDirectory "$safeName.csv") -NoTypeInformation -Encoding utf8
}

$priorSafe = @(Import-Csv -LiteralPath $PriorSafePath)
$consolidated = @(
    $priorSafe | ForEach-Object {
        [pscustomobject]@{
            product_id=$_.product_id; barcode=$_.barcode; product_name=$_.product_name; brand=$_.brand
            evidence_stage='APPLY_READY_PRIOR_EVIDENCE'; nutrition_gate='PASSED_PRIOR_EXACT_GATE'
            quantity=$_.source_quantity; categories=$_.source_categories
        }
    }
    $results | Where-Object decision -eq 'ML_PHYSICAL_FORM_SOURCE_SUPPORTED' | ForEach-Object {
        [pscustomobject]@{
            product_id=$_.product_id; barcode=$_.barcode; product_name=$_.local_name; brand=$_.brand
            evidence_stage='PHYSICAL_ML_SOURCE_SUPPORTED'; nutrition_gate='RECONCILIATION_REQUIRED'
            quantity=$_.quantity; categories=$_.categories
        }
    }
    $results | Where-Object decision -eq 'ML_PHYSICAL_FORM_UNIT_MISSING' | ForEach-Object {
        [pscustomobject]@{
            product_id=$_.product_id; barcode=$_.barcode; product_name=$_.local_name; brand=$_.brand
            evidence_stage='LIQUID_IDENTITY_SUPPORTED'; nutrition_gate='VOLUME_UNIT_AND_NUTRITION_EVIDENCE_REQUIRED'
            quantity=$_.quantity; categories=$_.categories
        }
    }
)
$consolidated | Sort-Object evidence_stage,product_name,product_id |
    Export-Csv -LiteralPath (Join-Path $OutputDirectory 'consolidated-physical-liquid-candidates.csv') -NoTypeInformation -Encoding utf8

$summary = [ordered]@{
    generatedAt = [DateTime]::UtcNow.ToString('o')
    evidenceFiles = $results.Count
    decisions = @($results | Group-Object decision | Sort-Object Name | ForEach-Object {
        [ordered]@{ decision=$_.Name; count=$_.Count }
    })
    physicalMlSupported = @($results | Where-Object decision -eq 'ML_PHYSICAL_FORM_SOURCE_SUPPORTED').Count
    nutritionReconciliationRequired = @($results | Where-Object nutrition_reconciliation_required -eq $true).Count
    priorApplyReady = $priorSafe.Count
    confirmedPhysicalLiquidTotal = $consolidated.Count
    liquidIdentityButUnitMissing = @($results | Where-Object decision -eq 'ML_PHYSICAL_FORM_UNIT_MISSING').Count
    safety = 'Physical ml form does not by itself authorize nutrition conversion. Nutrition basis reconciliation remains mandatory before DB apply.'
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary.json') -Encoding utf8
$summary | ConvertTo-Json -Depth 5
