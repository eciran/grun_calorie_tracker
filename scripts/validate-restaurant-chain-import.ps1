param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [ValidateSet('REVIEW','PRODUCTION')][string]$Mode = 'REVIEW',
    [Parameter(Mandatory = $true)][string]$ReportPath,
    [switch]$FailOnError
)

$ErrorActionPreference = 'Stop'
$rows = @(Import-Csv -LiteralPath (Resolve-Path -LiteralPath $InputPath))
$errors = [System.Collections.Generic.List[object]]::new()

function Add-Error($row, $code) {
    $errors.Add([pscustomobject]@{ source_key = $row.source_key; code = $code })
}

$seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($row in $rows) {
    if ($row.partition -ne 'BRANDED_PRODUCT_RESTAURANT_CHAIN_REVIEW') { Add-Error $row 'INVALID_PARTITION' }
    if ($row.restaurant_identity -notmatch '^restaurant:(tr|uk-ie):[a-z0-9-]+:[a-z0-9-]+$') { Add-Error $row 'INVALID_RESTAURANT_IDENTITY' }
    elseif (-not $seen.Add($row.restaurant_identity)) { Add-Error $row 'DUPLICATE_RESTAURANT_IDENTITY' }
    if (-not [string]::IsNullOrWhiteSpace($row.barcode)) { Add-Error $row 'BARCODE_NOT_ALLOWED_FOR_RESTAURANT_ITEM' }
    if ([string]::IsNullOrWhiteSpace($row.brand) -or [string]::IsNullOrWhiteSpace($row.name)) { Add-Error $row 'MISSING_IDENTITY_FIELD' }
    if ($row.market_region -notin @('TR','UK_IE')) { Add-Error $row 'UNSUPPORTED_MARKET' }
    if ($row.category -notin @('BURGER','SANDWICH','WRAP','PIZZA','CHICKEN')) { Add-Error $row 'NON_CORE_CATEGORY' }
    foreach ($field in @('calories_per_serving','protein_g_per_serving','carbs_g_per_serving','fat_g_per_serving')) {
        $value = 0.0
        if (-not [double]::TryParse(([string]$row.$field).Replace(',','.'), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$value) -or $value -lt 0) {
            Add-Error $row "INVALID_$($field.ToUpperInvariant())"
        }
    }
    if ($row.macro_gate -match 'HARD') { Add-Error $row 'MACRO_HARD_REVIEW' }
    if ($Mode -eq 'PRODUCTION') {
        if ($row.rights_status -notin @('APPROVED','OWNER_AUTHORIZED')) { Add-Error $row 'RIGHTS_NOT_APPROVED' }
        $hasPer100 = -not [string]::IsNullOrWhiteSpace($row.calories_per_100g)
        $hasWeight = -not [string]::IsNullOrWhiteSpace($row.serving_size_grams)
        if (-not $hasPer100 -and -not $hasWeight) { Add-Error $row 'NO_PER100_OR_VERIFIED_WEIGHT' }
        if ($row.record_approval -ne 'APPROVED') { Add-Error $row 'RECORD_NOT_APPROVED' }
    }
}

$report = [ordered]@{
    schema_version = 'restaurant-chain-import-contract-v1'
    mode = $Mode
    rows = $rows.Count
    unique_restaurant_identities = $seen.Count
    gate_passed = $errors.Count -eq 0
    error_count = $errors.Count
    error_counts = [ordered]@{}
    errors = @($errors)
}
foreach ($group in ($errors | Group-Object code | Sort-Object Name)) { $report.error_counts[$group.Name] = $group.Count }
$parent = Split-Path -Parent $ReportPath
if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ReportPath -Encoding utf8
$report | ConvertTo-Json -Depth 8
if ($FailOnError -and $errors.Count -gt 0) { throw "Restaurant-chain $Mode gate failed with $($errors.Count) errors." }
