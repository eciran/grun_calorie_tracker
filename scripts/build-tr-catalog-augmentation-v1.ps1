param(
    [string]$OffStrictPath = ".\outputs\product-catalog-aug08\licensed-default-20260802\import-chunks\off-tr-strict-part-001.csv",
    [string]$RetailerRichPath = ".\outputs\product-catalog-aug08\tr-retailer-test-v1\tr-retailer-test-rich-import.csv",
    [string]$GenericPath = ".\outputs\tr-generic-catalog-v2.csv",
    [string]$OutputDirectory = ".\outputs\product-catalog-aug21\tr-augmentation-v1",
    [string]$ReleaseId = "tr-catalog-augmentation-v1-20260821",
    [int]$ExpectedRetailerRows = 1605
)

$ErrorActionPreference = 'Stop'
$chunkRoot = Join-Path $OutputDirectory 'import-chunks'
New-Item -ItemType Directory -Path $chunkRoot -Force | Out-Null
$off = @(Import-Csv -LiteralPath $OffStrictPath)
$retailer = @(Import-Csv -LiteralPath $RetailerRichPath)
$generic = @(Import-Csv -LiteralPath $GenericPath)
$offBarcodes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$off | ForEach-Object { [void]$offBarcodes.Add($_.barcode) }
$overlap = @($retailer | Where-Object { $offBarcodes.Contains($_.barcode) }).Count
if ($off.Count -ne 1595 -or $retailer.Count -ne $ExpectedRetailerRows -or $generic.Count -ne 184) { throw 'Unexpected source row count.' }

$retailerTarget = Join-Path $chunkRoot 'tr-retailer-rich-private-part-001.csv'
$genericTarget = Join-Path $chunkRoot 'tr-generic-catalog-v2-part-001.csv'
Copy-Item -LiteralPath $RetailerRichPath -Destination $retailerTarget -Force
Copy-Item -LiteralPath $GenericPath -Destination $genericTarget -Force
$manifest = [ordered]@{
    schemaVersion=1; releaseId=$ReleaseId; generatedAt=[DateTimeOffset]::UtcNow.ToString('o')
    releaseClassification='STAGING_PRIVATE_TEST_ONLY'; productionSafe=$false
    requiredImportMode='RAW_EXTERNAL'; requiredImportFormat='GRUN_STANDARD'
    baseline=[ordered]@{offStrictBranded=$off.Count; existingGeneric=146}
    expected=[ordered]@{retailerRows=$retailer.Count;retailerOverlapWithOff=$overlap;retailerNetNew=$retailer.Count-$overlap;genericRows=$generic.Count;estimatedBrandedAfterImport=$off.Count+$retailer.Count-$overlap;estimatedGenericAfterImport=185}
    chunks=@(
        [ordered]@{role='PRIVATE_TEST_BRANDED';file='import-chunks/tr-retailer-rich-private-part-001.csv';rows=$retailer.Count;sha256=(Get-FileHash $retailerTarget -Algorithm SHA256).Hash},
        [ordered]@{role='GENERIC_IMPORT';file='import-chunks/tr-generic-catalog-v2-part-001.csv';rows=$generic.Count;sha256=(Get-FileHash $genericTarget -Algorithm SHA256).Hash}
    )
    blockers=@(
        'Retailer records are private/staging only until commercial reuse and persistent-storage rights are recorded.',
        'The obsolete USDA_FOODDATA:fdc:171443 olive-oil/mayonnaise mismatch must be quarantined through the admin API.',
        'A catalog-manage ADMIN_CATALOG JWT is required for staging import.'
    )
}
$manifestPath = Join-Path $OutputDirectory 'manifest.json'
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$manifest | ConvertTo-Json -Depth 8
