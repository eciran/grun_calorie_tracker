param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $ManifestPath,

    [ValidateSet("pilot", "gate")]
    [string] $Stage = "pilot",

    [Parameter(Mandatory = $true)]
    [string] $ReportPath,

    [switch] $FailOnGate
)

$ErrorActionPreference = "Stop"

function Resolve-OutputFile {
    param([string] $Path)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    if ($parent) {
        return Join-Path (Resolve-Path -LiteralPath $parent).Path (Split-Path -Leaf $Path)
    }
    return Join-Path (Get-Location) $Path
}

function Test-DecimalValue {
    param([string] $Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    $parsed = 0.0
    return [double]::TryParse($Value.Replace(",", "."), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)
}

function Get-DecimalValue {
    param([string] $Value)
    if (-not (Test-DecimalValue $Value)) { return $null }
    return [double]::Parse($Value.Replace(",", "."), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture)
}

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedManifest = (Resolve-Path -LiteralPath $ManifestPath).Path
$resolvedReport = Resolve-OutputFile -Path $ReportPath
$manifest = Get-Content -LiteralPath $resolvedManifest -Raw -Encoding UTF8 | ConvertFrom-Json
$stageConfig = $manifest.stages.$Stage
if ($null -eq $stageConfig) { throw "Stage '$Stage' is missing from manifest." }
$targetRows = [int] $stageConfig.targetRows
$rows = @(Import-Csv -LiteralPath $resolvedInput -Encoding UTF8)

$duplicateBarcodeGroups = @($rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_.barcode) } | Group-Object barcode | Where-Object Count -gt 1).Count
$duplicateSourceKeyGroups = @($rows | Where-Object { -not [string]::IsNullOrWhiteSpace($_.source_key) } | Group-Object source_key | Where-Object Count -gt 1).Count
$invalidBarcodeRows = 0
$missingCoreNutritionRows = 0
$missingBrandRows = 0
$missingNameRows = 0
$implausibleNutritionRows = 0
$marketMismatchRows = 0
$marketAvailabilityMismatchRows = 0
$localizedEnglishNameRows = 0
$localizedTurkishNameRows = 0
$englishAliasRows = 0
$turkishAliasRows = 0
$catalogMismatchRows = 0
$sourceMismatchRows = 0
$nutritionBasisMismatchRows = 0
$missingServingRows = 0
$suspiciousNameRows = 0

foreach ($row in $rows) {
    if ([string]::IsNullOrWhiteSpace($row.barcode) -or $row.barcode -notmatch "^\d{8,14}$") { $invalidBarcodeRows++ }
    if ([string]::IsNullOrWhiteSpace($row.brand)) { $missingBrandRows++ }
    if ([string]::IsNullOrWhiteSpace($row.name)) { $missingNameRows++ }
    if ($row.market_region -ne $manifest.marketRegion) { $marketMismatchRows++ }
    $availabilityValue = if ([string]::IsNullOrWhiteSpace($row.market_regions)) { $row.market_region } else { $row.market_regions }
    $availableMarkets = @($availabilityValue -split "[,;|]" | ForEach-Object { $_.Trim().ToUpperInvariant() } | Where-Object { $_ })
    if ($availableMarkets -notcontains $manifest.marketRegion) { $marketAvailabilityMismatchRows++ }
    if (-not [string]::IsNullOrWhiteSpace($row.display_name_en)) { $localizedEnglishNameRows++ }
    if (-not [string]::IsNullOrWhiteSpace($row.display_name_tr)) { $localizedTurkishNameRows++ }
    if (-not [string]::IsNullOrWhiteSpace($row.aliases_en)) { $englishAliasRows++ }
    if (-not [string]::IsNullOrWhiteSpace($row.aliases_tr)) { $turkishAliasRows++ }
    if ($row.catalog_type -ne $manifest.catalogType) { $catalogMismatchRows++ }
    if ($row.data_source -ne $manifest.dataSource) { $sourceMismatchRows++ }
    if ($row.nutrition_basis -ne $manifest.nutritionBasis) { $nutritionBasisMismatchRows++ }
    if ([string]::IsNullOrWhiteSpace($row.serving_size_grams)) { $missingServingRows++ }
    if (-not [string]::IsNullOrWhiteSpace($row.name) -and $row.name -cmatch "^[a-z0-9 ,.'()&+\-/]+$") { $suspiciousNameRows++ }

    $hasCore = (Test-DecimalValue $row.calories) -and (Test-DecimalValue $row.protein) -and (Test-DecimalValue $row.fat) -and (Test-DecimalValue $row.carbs)
    if (-not $hasCore) {
        $missingCoreNutritionRows++
        continue
    }
    $calories = Get-DecimalValue $row.calories
    $protein = Get-DecimalValue $row.protein
    $fat = Get-DecimalValue $row.fat
    $carbs = Get-DecimalValue $row.carbs
    if ($calories -lt 0 -or $calories -gt 1000 -or $protein -lt 0 -or $protein -gt 100 -or $fat -lt 0 -or $fat -gt 100 -or $carbs -lt 0 -or $carbs -gt 100 -or ($protein + $fat + $carbs) -gt 110) {
        $implausibleNutritionRows++
    }
}

$preferredLanguage = if ($null -eq $manifest.languageReview) { $null } else { $manifest.languageReview.preferredLanguage }
$preferredLocalizedNameRows = switch ($preferredLanguage) {
    "TR" { $localizedTurkishNameRows }
    "EN" { $localizedEnglishNameRows }
    default { 0 }
}
$missingPreferredLocalizationRows = if ($null -eq $preferredLanguage) { 0 } else { $rows.Count - $preferredLocalizedNameRows }
$rowCount = $rows.Count
$acceptedPercent = if ($targetRows -eq 0) { 0 } else { [Math]::Round(($rowCount * 100.0) / $targetRows, 2) }
$missingServingPercent = if ($rowCount -eq 0) { 100 } else { [Math]::Round(($missingServingRows * 100.0) / $rowCount, 2) }
$failures = [System.Collections.Generic.List[string]]::new()
$gate = $manifest.qualityGate
$minimumRows = [int][Math]::Ceiling($targetRows * ([double] $gate.minimumAcceptedPercent / 100.0))
if ($rowCount -lt $minimumRows) { $failures.Add("ACCEPTED_ROWS_BELOW_TARGET") }
if ($duplicateBarcodeGroups -gt [int] $gate.maximumDuplicateBarcodeGroups) { $failures.Add("DUPLICATE_BARCODES") }
if ($duplicateSourceKeyGroups -gt [int] $gate.maximumDuplicateSourceKeyGroups) { $failures.Add("DUPLICATE_SOURCE_KEYS") }
if ($invalidBarcodeRows -gt [int] $gate.maximumInvalidBarcodeRows) { $failures.Add("INVALID_BARCODES") }
if ($missingCoreNutritionRows -gt [int] $gate.maximumMissingCoreNutritionRows) { $failures.Add("MISSING_CORE_NUTRITION") }
if ($missingBrandRows -gt [int] $gate.maximumMissingBrandRows) { $failures.Add("MISSING_BRAND") }
if ($missingNameRows -gt [int] $gate.maximumMissingNameRows) { $failures.Add("MISSING_NAME") }
if ($implausibleNutritionRows -gt [int] $gate.maximumImplausibleNutritionRows) { $failures.Add("IMPLAUSIBLE_NUTRITION") }
if ($marketMismatchRows -gt [int] $gate.maximumMarketMismatchRows) { $failures.Add("MARKET_MISMATCH") }
if ($marketAvailabilityMismatchRows -gt [int] $gate.maximumMarketAvailabilityMismatchRows) { $failures.Add("MARKET_AVAILABILITY_MISMATCH") }
if ($catalogMismatchRows -gt 0) { $failures.Add("CATALOG_TYPE_MISMATCH") }
if ($sourceMismatchRows -gt 0) { $failures.Add("DATA_SOURCE_MISMATCH") }
if ($nutritionBasisMismatchRows -gt 0) { $failures.Add("NUTRITION_BASIS_MISMATCH") }
if ($missingServingPercent -gt [double] $gate.maximumMissingServingPercent) { $failures.Add("MISSING_SERVING_THRESHOLD") }

$topBrands = @($rows | Group-Object brand | Sort-Object Count -Descending | Select-Object -First 20 | ForEach-Object { [ordered]@{ brand = $_.Name; count = $_.Count } })
$report = [ordered]@{
    schemaVersion = 1
    manifestId = $manifest.manifestId
    stage = $Stage
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    artifact = [ordered]@{ path = $resolvedInput; sha256 = (Get-FileHash -LiteralPath $resolvedInput -Algorithm SHA256).Hash }
    targetRows = $targetRows
    rows = $rowCount
    acceptedPercent = $acceptedPercent
    gatePassed = $failures.Count -eq 0
    failures = @($failures)
    blocking = [ordered]@{
        duplicateBarcodeGroups = $duplicateBarcodeGroups
        duplicateSourceKeyGroups = $duplicateSourceKeyGroups
        invalidBarcodeRows = $invalidBarcodeRows
        missingCoreNutritionRows = $missingCoreNutritionRows
        missingBrandRows = $missingBrandRows
        missingNameRows = $missingNameRows
        implausibleNutritionRows = $implausibleNutritionRows
        marketMismatchRows = $marketMismatchRows
        marketAvailabilityMismatchRows = $marketAvailabilityMismatchRows
        catalogMismatchRows = $catalogMismatchRows
        sourceMismatchRows = $sourceMismatchRows
        nutritionBasisMismatchRows = $nutritionBasisMismatchRows
    }
    warnings = [ordered]@{
        missingServingRows = $missingServingRows
        missingServingPercent = $missingServingPercent
        suspiciousNameRows = $suspiciousNameRows
        localizedEnglishNameRows = $localizedEnglishNameRows
        localizedTurkishNameRows = $localizedTurkishNameRows
        englishAliasRows = $englishAliasRows
        turkishAliasRows = $turkishAliasRows
        preferredLanguage = $preferredLanguage
        missingPreferredLocalizationRows = $missingPreferredLocalizationRows
    }
    diversity = [ordered]@{
        uniqueBrands = @($rows | Select-Object -ExpandProperty brand -Unique).Count
        topBrands = $topBrands
    }
}
[IO.File]::WriteAllText($resolvedReport, (($report | ConvertTo-Json -Depth 8) + "`n"), [Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json -Depth 8
if ($FailOnGate -and $failures.Count -gt 0) {
    throw "Food import batch gate failed: $($failures -join ', ')"
}