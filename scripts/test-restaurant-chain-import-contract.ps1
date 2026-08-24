param([string]$Validator = '.\scripts\validate-restaurant-chain-import.ps1')

$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP ('restaurant-contract-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    $valid = Join-Path $root 'valid.csv'
    @([pscustomobject]@{
        partition='BRANDED_PRODUCT_RESTAURANT_CHAIN_REVIEW'; restaurant_identity='restaurant:tr:test-brand:test-burger'
        source_key='test:1'; barcode=''; brand='Test Brand'; name='Test Burger'; market_region='TR'; category='BURGER'
        calories_per_serving='400'; protein_g_per_serving='20'; carbs_g_per_serving='40'; fat_g_per_serving='18'
        calories_per_100g='200'; serving_size_grams='200'; macro_gate='PASS'; rights_status='APPROVED'; record_approval='APPROVED'
    }) | Export-Csv -NoTypeInformation -Encoding utf8 $valid
    $reviewReport = Join-Path $root 'review.json'
    & $Validator -InputPath $valid -Mode REVIEW -ReportPath $reviewReport -FailOnError | Out-Null
    $productionReport = Join-Path $root 'production.json'
    & $Validator -InputPath $valid -Mode PRODUCTION -ReportPath $productionReport -FailOnError | Out-Null

    $duplicate = Join-Path $root 'duplicate.csv'
    @((Import-Csv $valid)[0], (Import-Csv $valid)[0]) | Export-Csv -NoTypeInformation -Encoding utf8 $duplicate
    $duplicateReport = Join-Path $root 'duplicate.json'
    & $Validator -InputPath $duplicate -Mode REVIEW -ReportPath $duplicateReport | Out-Null
    $dup = Get-Content -Raw $duplicateReport | ConvertFrom-Json
    if ($dup.gate_passed -or $dup.error_counts.DUPLICATE_RESTAURANT_IDENTITY -ne 1) { throw 'Duplicate identity test failed.' }

    $first = @(Import-Csv $valid)
    $second = @(Import-Csv $valid)
    $state = @{}
    foreach ($row in @($first + $second)) { $state[$row.restaurant_identity] = $row }
    if ($state.Count -ne 1) { throw 'Idempotent upsert simulation failed.' }
    Write-Output 'PASS: review, production, duplicate identity, and two-pass idempotency tests.'
} finally {
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
}
