$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $testDirectory = ".\outputs\product-data-readiness\self-test"
    if (-not (Test-Path -LiteralPath $testDirectory)) { New-Item -ItemType Directory -Path $testDirectory | Out-Null }

    $manifest = Get-Content .\sample-data\manifests\open-food-facts-uk-ie-v1.json -Raw | ConvertFrom-Json
    $manifest.stages.pilot.targetRows = 2
    $manifestPath = Join-Path $testDirectory "manifest.json"
    [IO.File]::WriteAllText($manifestPath, (($manifest | ConvertTo-Json -Depth 10) + "`n"), [Text.UTF8Encoding]::new($false))

    $validRows = @(
        [pscustomobject]@{ catalog_type="BRANDED_PRODUCT"; data_source="OPEN_FOOD_FACTS"; nutrition_basis="SOURCE_REPORTED"; barcode="5010000000001"; source_key="barcode:5010000000001"; name="Whole Milk"; brand="Test Dairy"; calories="64"; protein="3.3"; fat="3.6"; carbs="4.7"; serving_size_grams="200"; market_region="UK_IE" },
        [pscustomobject]@{ catalog_type="BRANDED_PRODUCT"; data_source="OPEN_FOOD_FACTS"; nutrition_basis="SOURCE_REPORTED"; barcode="5010000000002"; source_key="barcode:5010000000002"; name="Brown Bread"; brand="Test Bakery"; calories="245"; protein="9"; fat="3"; carbs="44"; serving_size_grams="40"; market_region="UK_IE" }
    )
    $validPath = Join-Path $testDirectory "valid.csv"
    $validRows | Export-Csv -LiteralPath $validPath -NoTypeInformation -Encoding UTF8
    $validOutput = & .\scripts\validate-food-import-batch.ps1 -InputPath $validPath -ManifestPath $manifestPath -Stage pilot -ReportPath (Join-Path $testDirectory "valid-report.json") -FailOnGate
    $validReport = ($validOutput -join "`n") | ConvertFrom-Json
    if (-not $validReport.gatePassed) { throw "Valid market batch did not pass." }

    $invalidRows = @(
        [pscustomobject]@{ catalog_type="BRANDED_PRODUCT"; data_source="OPEN_FOOD_FACTS"; nutrition_basis="SOURCE_REPORTED"; barcode="5010000000001"; source_key="barcode:5010000000001"; name=""; brand=""; calories="1200"; protein="120"; fat="0"; carbs="0"; serving_size_grams=""; market_region="EU" },
        [pscustomobject]@{ catalog_type="BRANDED_PRODUCT"; data_source="OPEN_FOOD_FACTS"; nutrition_basis="SOURCE_REPORTED"; barcode="5010000000001"; source_key="barcode:5010000000001"; name="Duplicate"; brand="Test"; calories="100"; protein="2"; fat="2"; carbs="10"; serving_size_grams="100"; market_region="UK_IE" }
    )
    $invalidPath = Join-Path $testDirectory "invalid.csv"
    $invalidRows | Export-Csv -LiteralPath $invalidPath -NoTypeInformation -Encoding UTF8
    $invalidOutput = & .\scripts\validate-food-import-batch.ps1 -InputPath $invalidPath -ManifestPath $manifestPath -Stage pilot -ReportPath (Join-Path $testDirectory "invalid-report.json")
    $invalidReport = ($invalidOutput -join "`n") | ConvertFrom-Json
    $requiredFailures = @("DUPLICATE_BARCODES", "DUPLICATE_SOURCE_KEYS", "MISSING_BRAND", "MISSING_NAME", "IMPLAUSIBLE_NUTRITION", "MARKET_MISMATCH")
    foreach ($failure in $requiredFailures) {
        if ($invalidReport.failures -notcontains $failure) { throw "Invalid market batch did not report $failure." }
    }

    $sourcePath = Join-Path $testDirectory "country-source.tsv"
    $sourceLines = @(
        "code`tproduct_name`tbrands`tproduct_name_en`tcountries_tags`tenergy-kcal_100g`tproteins_100g`tfat_100g`tcarbohydrates_100g",
        "5010000000001`t`tTest Dairy`tUK Milk`ten:united-kingdom,uk`t64`t3.3`t3.6`t4.7",
        "4820000000001`tUkraine Milk`tOther Dairy`t`ten:ukraine`t64`t3.3`t3.6`t4.7",
        "5010000000003`tBroken UK Food`tBad Brand`t`ten:united-kingdom,uk`t1200`t120`t0`t0"
    )
    [IO.File]::WriteAllLines($sourcePath, $sourceLines, [Text.UTF8Encoding]::new($false))
    $countryOutputPath = Join-Path $testDirectory "country-output.tsv"
    $exportOutput = & .\scripts\export-open-food-facts-bulk-products.ps1 -InputPath $sourcePath -OutputPath $countryOutputPath -MarketRegion UK_IE -CountryTerms @("uk") -Limit 10 -MaxRowsToRead 10 -RequireCalories -RequireCompleteMacroData -RequireBrand -RejectImplausibleNutrition
    $exportReport = ($exportOutput -join "`n") | ConvertFrom-Json
    $exportedRows = @(Import-Csv -LiteralPath $countryOutputPath -Delimiter "`t")
    if ($exportReport.rowsWritten -ne 1 -or $exportReport.countryFilteredRows -ne 1 -or $exportReport.implausibleNutritionRows -ne 1 -or $exportedRows[0].product_name -ne "UK Milk") {
        throw "Country-token or nutrition plausibility rule test failed."
    }

    $trOverlapPath = Join-Path $testDirectory "overlap-tr.csv"
    $euOverlapPath = Join-Path $testDirectory "overlap-eu.csv"
    @([pscustomobject]@{ barcode="8690000000001"; source_key="barcode:8690000000001"; name="Ortak Ürün"; calories="100"; protein="2"; fat="3"; carbs="15"; market_region="TR" }) |
        Export-Csv -LiteralPath $trOverlapPath -NoTypeInformation -Encoding UTF8
    @([pscustomobject]@{ barcode="8690000000001"; source_key="barcode:8690000000001"; name="Shared Product"; calories="100.0"; protein="2.0"; fat="3"; carbs="15"; market_region="EU" }) |
        Export-Csv -LiteralPath $euOverlapPath -NoTypeInformation -Encoding UTF8
    $overlapOutput = & .\scripts\validate-food-import-market-overlap.ps1 -InputPaths @($trOverlapPath, $euOverlapPath) -ReportPath (Join-Path $testDirectory "overlap-report.json") -FailOnGate
    $overlapReport = ($overlapOutput -join "`n") | ConvertFrom-Json
    if (-not $overlapReport.gatePassed -or $overlapReport.crossMarketOverlapGroups -ne 1 -or $overlapReport.rowsMergedByStableIdentity -ne 1) {
        throw "Compatible cross-market identity was not classified as a safe merge."
    }

    [ordered]@{ passed = 4; failed = 0; checks = @("valid batch passes", "invalid batch is classified", "UK token and nutrition filters are exact", "cross-market identity merge is safe") } | ConvertTo-Json -Depth 4
}
finally {
    Pop-Location
}