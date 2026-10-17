param(
    [string]$CandidatePath = ".\outputs\serving-option-candidates\liquid-review\live-off-analysis\ml_physical_form_source_supported.csv",
    [string]$EvidenceDirectory = ".\outputs\serving-option-candidates\liquid-review\nutrition-evidence\items",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review\nutrition-reconciliation",
    [double]$RelativeTolerance = 0.03,
    [double]$AbsoluteTolerance = 0.15
)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Get-Location).Path)
foreach ($path in @($CandidatePath, $EvidenceDirectory, $OutputDirectory)) {
    $resolved = [IO.Path]::GetFullPath((Join-Path $workspace $path))
    if (-not $resolved.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
        throw "All paths must stay inside the workspace."
    }
}

$candidates = @(Import-Csv -LiteralPath $CandidatePath)
$envFile = Join-Path $workspace '.env'
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $key, $value = $line.Split('=', 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), 'Process')
        }
    }
}
$dbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { 'postgres' }
$dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { 'grun_calorie_db' }
$ids = ($candidates.product_id | ForEach-Object { [long]$_ }) -join ','
$sql = "select id,name,calories,protein,carbs,fat,fiber,sugar,nutrition_reference_unit,serving_unit from food_items where id in ($ids) order by id"
$dbJson = docker exec -e PGPASSWORD=$env:POSTGRES_PASSWORD grun-postgres psql -U $dbUser -d $dbName -At -c "select coalesce(json_agg(t),'[]'::json) from ($sql) t;"
if ($LASTEXITCODE -ne 0) { throw 'Unable to read local food_items.' }
$dbById = @{}
$parsedDbRows = $dbJson | ConvertFrom-Json
foreach ($dbRow in $parsedDbRows) {
    $dbById[[string]$dbRow.id] = $dbRow
}
if ($dbById.Count -ne $candidates.Count) {
    throw "Expected $($candidates.Count) local products but loaded $($dbById.Count). Refusing incomplete reconciliation."
}

function Read-Number($object, [string]$name) {
    if ($null -eq $object) { return $null }
    $property = $object.PSObject.Properties[$name]
    if ($null -eq $property -or $null -eq $property.Value -or [string]$property.Value -eq '') { return $null }
    return [double]$property.Value
}
function Is-Close($left, $right) {
    if ($null -eq $left -or $null -eq $right) { return $null }
    $limit = [Math]::Max($AbsoluteTolerance, [Math]::Abs([double]$right) * $RelativeTolerance)
    return [Math]::Abs([double]$left - [double]$right) -le $limit
}

$results = foreach ($candidate in $candidates) {
    $db = $dbById[[string]$candidate.product_id]
    $file = Join-Path $EvidenceDirectory "barcode-$($candidate.barcode).json"
    $evidence = if (Test-Path -LiteralPath $file) { Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json } else { $null }
    $product = $evidence.product
    $n = $product.nutriments
    $sourceBasis = [string]$product.nutrition_data_per
    $energyKcal100 = Read-Number $n 'energy-kcal_100g'
    $protein100 = Read-Number $n 'proteins_100g'
    $carbs100 = Read-Number $n 'carbohydrates_100g'
    $fat100 = Read-Number $n 'fat_100g'
    $fiber100 = Read-Number $n 'fiber_100g'
    $sugar100 = Read-Number $n 'sugars_100g'
    $checks = @(
        Is-Close (Read-Number $db 'calories') $energyKcal100
        Is-Close (Read-Number $db 'protein') $protein100
        Is-Close (Read-Number $db 'carbs') $carbs100
        Is-Close (Read-Number $db 'fat') $fat100
    ) | Where-Object { $null -ne $_ }
    $matches = @($checks | Where-Object { $_ }).Count
    $mismatches = @($checks | Where-Object { -not $_ }).Count
    $decision = if ($null -eq $evidence -or $evidence.status -ne 'FETCHED') {
        'SOURCE_UNAVAILABLE'
    } elseif ($null -eq $energyKcal100 -or $checks.Count -lt 3) {
        'INSUFFICIENT_NUTRITION_EVIDENCE'
    } elseif ($mismatches -eq 0 -and $matches -ge 3) {
        'LOCAL_VALUES_MATCH_SOURCE_100_UNIT'
    } else {
        'LOCAL_SOURCE_MISMATCH_REVIEW'
    }
    [pscustomobject]@{
        product_id=$candidate.product_id; barcode=$candidate.barcode; product_name=$candidate.local_name; brand=$candidate.brand
        decision=$decision; source_nutrition_data_per=$sourceBasis; local_reference_unit=$db.nutrition_reference_unit
        local_serving_unit=$db.serving_unit
        compared_fields=$checks.Count; matching_fields=$matches; mismatching_fields=$mismatches
        local_calories=$db.calories; source_calories_100=$energyKcal100
        local_protein=$db.protein; source_protein_100=$protein100
        local_carbs=$db.carbs; source_carbs_100=$carbs100
        local_fat=$db.fat; source_fat_100=$fat100
        source_fiber_100=$fiber100; source_sugar_100=$sugar100
        product_quantity_unit=[string]$product.product_quantity_unit
        serving_quantity_unit=[string]$product.serving_quantity_unit
        serving_size=[string]$product.serving_size
    }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$results | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'all-reconciliations.csv') -NoTypeInformation -Encoding utf8
foreach ($group in $results | Group-Object decision) {
    $group.Group | Export-Csv -LiteralPath (Join-Path $OutputDirectory ($group.Name.ToLowerInvariant() + '.csv')) -NoTypeInformation -Encoding utf8
}
$summary = [ordered]@{
    generatedAt=[DateTime]::UtcNow.ToString('o'); candidates=$candidates.Count
    decisions=@($results | Group-Object decision | Sort-Object Name | ForEach-Object { [ordered]@{ decision=$_.Name; count=$_.Count } })
    rule="At least three available local/source core fields must match within max($AbsoluteTolerance absolute, $RelativeTolerance relative); physical liquid evidence remains a separate gate."
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary.json') -Encoding utf8
$summary | ConvertTo-Json -Depth 5
