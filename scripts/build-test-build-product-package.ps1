param(
    [string]$OutputDirectory = ".\outputs\local-food-catalog-aug08\release\test-build-db-20260809"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$output = Join-Path $root $OutputDirectory
$licensedDir = Join-Path $root "outputs\product-catalog-aug08\licensed-default-20260802"
$licensedManifestPath = Join-Path $licensedDir "bundle-manifest.json"
$licensedRehearsalPath = Join-Path $licensedDir "rehearsal\rehearsal-report.json"
$overlayPath = Join-Path $root "outputs\product-catalog-aug08\tr-retailer-test-v10-expanded-barcode-20260807\tr-retailer-test-rich-v10-import.csv"
$genericEnhancedPath = Join-Path $root "outputs\local-food-catalog-aug08\release\d2-usda-generic-release-ready.csv"
$taxonomyPath = Join-Path $root "outputs\local-food-catalog-aug08\staging\d4-local-dish-taxonomy-frozen-v2.csv"
$localReviewPath = Join-Path $root "outputs\local-food-catalog-aug08\review\d4-tr-calculated-preflight-pilot-01.csv"
$restaurantPath = Join-Path $root "outputs\local-food-catalog-aug08\review\restaurant-chain\six-brand-core-main-review-pass-44.csv"
$restaurantRehearsal1 = Join-Path $root "outputs\local-food-catalog-aug08\review\restaurant-chain\rehearsal-01\rehearsal-report.json"
$restaurantRehearsal2 = Join-Path $root "outputs\local-food-catalog-aug08\review\restaurant-chain\rehearsal-02\rehearsal-report.json"

$required = @($licensedManifestPath, $licensedRehearsalPath, $overlayPath, $genericEnhancedPath,
    $taxonomyPath, $localReviewPath, $restaurantPath, $restaurantRehearsal1, $restaurantRehearsal2)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required artifact: $path" }
}

$licensed = Get-Content -LiteralPath $licensedManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$licensedRehearsal = Get-Content -LiteralPath $licensedRehearsalPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($licensedRehearsal.status -ne "PASS") { throw "Licensed rehearsal is not PASS." }

$verifiedLicensedRows = 0
$licensedBarcodes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($chunk in @($licensed.artifacts | ForEach-Object { $_.chunks })) {
    $chunkPath = Join-Path $licensedDir $chunk.file
    if (-not (Test-Path -LiteralPath $chunkPath -PathType Leaf)) { throw "Missing licensed chunk: $($chunk.file)" }
    if ((Get-FileHash -LiteralPath $chunkPath -Algorithm SHA256).Hash -ne $chunk.sha256) { throw "Licensed chunk hash mismatch: $($chunk.file)" }
    $verifiedLicensedRows += [int]$chunk.rows
    if ($chunk.role -eq "BRANDED_IMPORT") {
        foreach ($row in Import-Csv -LiteralPath $chunkPath) { if ($row.barcode) { [void]$licensedBarcodes.Add($row.barcode) } }
    }
}
if ($verifiedLicensedRows -ne [int]$licensed.counts.inputRows) { throw "Licensed row total mismatch." }

$overlay = @(Import-Csv -LiteralPath $overlayPath)
$overlayBarcodes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$overlayOverlap = 0
foreach ($row in $overlay) {
    if ($row.catalog_type -ne "BRANDED_PRODUCT" -or $row.market_region -ne "TR") { throw "Overlay partition mismatch." }
    if ($row.barcode -notmatch '^\d{8,14}$') { throw "Overlay invalid barcode: $($row.barcode)" }
    if (-not $overlayBarcodes.Add($row.barcode)) { throw "Overlay duplicate barcode: $($row.barcode)" }
    foreach ($field in @("name", "brand", "calories", "protein", "fat", "carbs")) {
        if ([string]::IsNullOrWhiteSpace($row.$field)) { throw "Overlay missing $field for $($row.barcode)" }
    }
    if ($licensedBarcodes.Contains($row.barcode)) { $overlayOverlap++ }
}

$genericEnhanced = @(Import-Csv -LiteralPath $genericEnhancedPath)
$taxonomy = @(Import-Csv -LiteralPath $taxonomyPath)
$localReview = @(Import-Csv -LiteralPath $localReviewPath)
$restaurant = @(Import-Csv -LiteralPath $restaurantPath)
$taxonomyTr = @($taxonomy | Where-Object market_region -eq "TR").Count
$taxonomyUkIe = @($taxonomy | Where-Object market_region -eq "UK_IE").Count

$references = @(
    [ordered]@{ id="licensed_default"; path=(Resolve-Path -Relative $licensedManifestPath); sha256=(Get-FileHash $licensedManifestPath -Algorithm SHA256).Hash },
    [ordered]@{ id="licensed_rehearsal"; path=(Resolve-Path -Relative $licensedRehearsalPath); sha256=(Get-FileHash $licensedRehearsalPath -Algorithm SHA256).Hash },
    [ordered]@{ id="tr_private_overlay_v10"; path=(Resolve-Path -Relative $overlayPath); sha256=(Get-FileHash $overlayPath -Algorithm SHA256).Hash },
    [ordered]@{ id="generic_enhanced_113"; path=(Resolve-Path -Relative $genericEnhancedPath); sha256=(Get-FileHash $genericEnhancedPath -Algorithm SHA256).Hash },
    [ordered]@{ id="local_dish_taxonomy"; path=(Resolve-Path -Relative $taxonomyPath); sha256=(Get-FileHash $taxonomyPath -Algorithm SHA256).Hash },
    [ordered]@{ id="local_dish_nutrition_review"; path=(Resolve-Path -Relative $localReviewPath); sha256=(Get-FileHash $localReviewPath -Algorithm SHA256).Hash },
    [ordered]@{ id="restaurant_chain_review"; path=(Resolve-Path -Relative $restaurantPath); sha256=(Get-FileHash $restaurantPath -Algorithm SHA256).Hash }
)

$manifest = [ordered]@{
    schemaVersion = 1
    releaseId = "grun-test-build-products-20260809"
    classification = "TEST_DB_READY_WITH_EXPLICIT_REVIEW_ONLY_PARTITIONS"
    generatedAt = "2026-08-09T00:00:00Z"
    databaseMutationPerformed = $false
    awsMutationPerformed = $false
    loadOrder = @("LICENSED_DEFAULT", "TR_PRIVATE_TEST_OVERLAY_V10")
    importReady = [ordered]@{
        licensedDefault = [ordered]@{
            enabledByDefault=$true; rows=[int]$licensed.counts.inputRows
            expectedCanonicalProducts=[int]$licensed.counts.expectedCanonicalProducts
            expectedCrossMarketMerges=[int]$licensed.counts.expectedCrossMarketMerges
            rehearsal="PASS_TWO_PASS_POSTGRESQL"; manifestReference="licensed_default"
        }
        trPrivateTestOverlayV10 = [ordered]@{
            enabledByDefault=$false; requiresExplicitTestOptIn=$true; rows=$overlay.Count
            existingCanonicalUpdates=$overlayOverlap; expectedNetNewCanonical=($overlay.Count-$overlayOverlap)
            expectedCombinedCanonicalAfterLicensed=([int]$licensed.counts.expectedCanonicalProducts+$overlay.Count-$overlayOverlap)
            validation="PASS_STATIC_IDENTITY_AND_CORE_NUTRITION"; artifactReference="tr_private_overlay_v10"
        }
    }
    reviewOnly = [ordered]@{
        genericEnhanced = [ordered]@{ rows=$genericEnhanced.Count; reason="Subset uses enhanced provenance contract but is not additive to the 146 generic rows already in licensed-default; canonical reconciliation required before replacement import." }
        localDish = [ordered]@{ taxonomyRows=$taxonomy.Count; tr=$taxonomyTr; ukIe=$taxonomyUkIe; nutritionReviewRows=$localReview.Count; importRows=0; reason="Curator approval and serving/release gates pending." }
        restaurantChain = [ordered]@{ reviewContractRows=$restaurant.Count; importRows=0; rehearsal="PASS_TWO_NO_DB_REHEARSALS"; reason="Rights, record approval, serving-weight/per-100 and application DB contract blockers remain." }
    }
    expectedAfterExplicitOverlay = [ordered]@{
        canonicalProducts=([int]$licensed.counts.expectedCanonicalProducts+$overlay.Count-$overlayOverlap)
        marketInputRows=([int]$licensed.counts.inputRows+$overlay.Count)
    }
    references = $references
    blockers = @(
        "LOCAL_DISH_IMPORT_EMPTY",
        "RESTAURANT_CHAIN_IMPORT_EMPTY",
        "GENERIC_ENHANCED_REPLACEMENT_RECONCILIATION_REQUIRED",
        "TR_PRIVATE_OVERLAY_DATABASE_TWO_PASS_REHEARSAL_NOT_YET_RECORDED"
    )
}

New-Item -ItemType Directory -Path $output -Force | Out-Null
$manifestPath = Join-Path $output "test-build-product-manifest.json"
[IO.File]::WriteAllText($manifestPath, (($manifest | ConvertTo-Json -Depth 12) + "`n"), [Text.UTF8Encoding]::new($false))
$report = [ordered]@{
    status="PASS"; manifest=(Resolve-Path -Relative $manifestPath)
    manifestSha256=(Get-FileHash $manifestPath -Algorithm SHA256).Hash
    licensedRows=[int]$licensed.counts.inputRows; overlayRows=$overlay.Count
    overlayUpdates=$overlayOverlap; overlayNetNew=($overlay.Count-$overlayOverlap)
    localDishImportRows=0; restaurantImportRows=0
}
$reportPath = Join-Path $output "preflight-report.json"
[IO.File]::WriteAllText($reportPath, (($report | ConvertTo-Json -Depth 6) + "`n"), [Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json -Depth 6
