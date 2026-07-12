param(
    [string] $ApiBaseUrl = "http://localhost:8080",
    [string] $Token = $env:GRUN_ADMIN_JWT,

    [ValidateSet("UK_IE", "TR", "EU", "GLOBAL")]
    [string] $MarketRegion = "UK_IE",

    [string[]] $Stores = @("tesco", "dunnes"),

    [ValidateRange(1, 10000)]
    [int] $Limit = 500,

    [string] $OutputPath = "",

    [ValidateSet("RAW_EXTERNAL", "CURATED_ADMIN")]
    [string] $ImportMode = "RAW_EXTERNAL",

    [ValidateSet("AUTO", "GRUN_STANDARD", "OPEN_FOOD_FACTS")]
    [string] $ImportFormat = "AUTO",

    [switch] $SkipExport,
    [switch] $SkipImport,
    [switch] $SkipReport,

    [switch] $EnforceReadiness,

    [ValidateRange(0, 100000)]
    [int] $MaxDuplicateBarcodeGroups = 0,

    [ValidateRange(0, 100000)]
    [int] $MaxMissingCalories = 0,

    [ValidateRange(0, 100000)]
    [int] $MaxMissingMacros = 0,

    [ValidateRange(0, 100000)]
    [int] $MaxGenericMissingPreparationState = 0,

    [ValidateRange(0, 100000)]
    [int] $MaxSuspiciousDisplayNames = 0,

    [ValidateRange(0, 100000)]
    [int] $MaxSkippedRows = 0,

    [switch] $RequireSearchResults
)

$ErrorActionPreference = "Stop"

function Resolve-BatchOutputPath {
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        return $OutputPath
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    return ".\outputs\food-catalog-$($MarketRegion.ToLowerInvariant())-$Limit-$timestamp.tsv"
}

function Get-IntValue {
    param(
        $Object,
        [string] $Name,
        [int] $DefaultValue = 0
    )

    if ($null -eq $Object) {
        return $DefaultValue
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value -or [string]::IsNullOrWhiteSpace([string] $property.Value)) {
        return $DefaultValue
    }

    return [int] $property.Value
}

function Get-MapIntValue {
    param(
        $Map,
        [string] $Name,
        [int] $DefaultValue = 0
    )

    if ($null -eq $Map) {
        return $DefaultValue
    }

    $property = $Map.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value -or [string]::IsNullOrWhiteSpace([string] $property.Value)) {
        return $DefaultValue
    }

    return [int] $property.Value
}

function New-ReadinessFailure {
    param(
        [string] $Code,
        [string] $Message,
        [int] $Actual,
        [int] $Allowed
    )

    return [pscustomobject] @{
        code = $Code
        message = $Message
        actual = $Actual
        allowed = $Allowed
    }
}

if ([string]::IsNullOrWhiteSpace($Token) -and (-not $SkipImport -or -not $SkipReport)) {
    throw "Admin JWT token is required. Pass -Token or set GRUN_ADMIN_JWT."
}

$resolvedOutput = Resolve-BatchOutputPath
$summary = [ordered] @{
    apiBaseUrl = $ApiBaseUrl
    marketRegion = $MarketRegion
    stores = $Stores
    limit = $Limit
    outputPath = $resolvedOutput
    export = $null
    import = $null
    report = $null
    readiness = $null
}

if (-not $SkipExport) {
    $exportJson = & powershell -ExecutionPolicy Bypass -File .\scripts\export-open-food-facts-store-products.ps1 `
        -OutputPath $resolvedOutput `
        -MarketRegion $MarketRegion `
        -Stores $Stores `
        -Limit $Limit

    $summary.export = $exportJson | ConvertFrom-Json
}

if (-not $SkipImport) {
    $importJson = & powershell -ExecutionPolicy Bypass -File .\scripts\import-food-pilot.ps1 `
        -ApiBaseUrl $ApiBaseUrl `
        -Token $Token `
        -FilePath $resolvedOutput `
        -ImportMode $ImportMode `
        -ImportFormat $ImportFormat

    $summary.import = $importJson | ConvertFrom-Json
}

if (-not $SkipReport) {
    $reportJson = & powershell -ExecutionPolicy Bypass -File .\scripts\report-food-import-pilot.ps1 `
        -ApiBaseUrl $ApiBaseUrl `
        -Token $Token `
        -MarketRegion $MarketRegion

    $summary.report = $reportJson | ConvertFrom-Json
}

$failures = @()

if ($summary.import) {
    $skippedRows = Get-IntValue -Object $summary.import -Name "skippedRows"
    if ($skippedRows -gt $MaxSkippedRows) {
        $failures += New-ReadinessFailure -Code "SKIPPED_ROWS" -Message "Import skipped more rows than allowed." -Actual $skippedRows -Allowed $MaxSkippedRows
    }

    $genericMissingPreparation = Get-MapIntValue -Map $summary.import.qualityWarningCounts -Name "GENERIC_MISSING_PREPARATION_STATE"
    if ($genericMissingPreparation -gt $MaxGenericMissingPreparationState) {
        $failures += New-ReadinessFailure -Code "GENERIC_MISSING_PREPARATION_STATE" -Message "Generic ingredients without explicit preparation state must be fixed or explicitly accepted before scaling." -Actual $genericMissingPreparation -Allowed $MaxGenericMissingPreparationState
    }

    $suspiciousDisplayNames = Get-MapIntValue -Map $summary.import.qualityWarningCounts -Name "SUSPICIOUS_DISPLAY_NAME"
    if ($suspiciousDisplayNames -gt $MaxSuspiciousDisplayNames) {
        $failures += New-ReadinessFailure -Code "SUSPICIOUS_DISPLAY_NAME" -Message "User-facing names require review before mobile exposure." -Actual $suspiciousDisplayNames -Allowed $MaxSuspiciousDisplayNames
    }
}

if ($summary.report) {
    $duplicateBarcodeGroups = [int] $summary.report.duplicateBarcodeGroups
    if ($duplicateBarcodeGroups -gt $MaxDuplicateBarcodeGroups) {
        $failures += New-ReadinessFailure -Code "DUPLICATE_BARCODE_GROUPS" -Message "Duplicate barcode groups exceed the allowed threshold." -Actual $duplicateBarcodeGroups -Allowed $MaxDuplicateBarcodeGroups
    }

    $missingCalories = Get-IntValue -Object $summary.report.summary -Name "missingCalories"
    if ($missingCalories -gt $MaxMissingCalories) {
        $failures += New-ReadinessFailure -Code "MISSING_CALORIES" -Message "Products with missing calories exceed the allowed threshold." -Actual $missingCalories -Allowed $MaxMissingCalories
    }

    $missingMacros = Get-IntValue -Object $summary.report.summary -Name "missingMacros"
    if ($missingMacros -gt $MaxMissingMacros) {
        $failures += New-ReadinessFailure -Code "MISSING_MACROS" -Message "Products with missing macros exceed the allowed threshold." -Actual $missingMacros -Allowed $MaxMissingMacros
    }

    if ($RequireSearchResults) {
        foreach ($searchResult in $summary.report.searchResults) {
            $totalElements = 0
            $isNumeric = [int]::TryParse([string] $searchResult.totalElements, [ref] $totalElements)
            if ((-not $isNumeric) -or $totalElements -le 0) {
                $failures += New-ReadinessFailure -Code "SEARCH_SMOKE_EMPTY" -Message "Search smoke term '$($searchResult.term)' did not return usable results." -Actual 0 -Allowed 1
            }
        }
    }
}

$summary.readiness = [pscustomobject] @{
    ready = $failures.Count -eq 0
    enforced = [bool] $EnforceReadiness
    thresholds = [pscustomobject] @{
        maxDuplicateBarcodeGroups = $MaxDuplicateBarcodeGroups
        maxMissingCalories = $MaxMissingCalories
        maxMissingMacros = $MaxMissingMacros
        maxGenericMissingPreparationState = $MaxGenericMissingPreparationState
        maxSuspiciousDisplayNames = $MaxSuspiciousDisplayNames
        maxSkippedRows = $MaxSkippedRows
        requireSearchResults = [bool] $RequireSearchResults
    }
    failures = $failures
}

[pscustomobject] $summary | ConvertTo-Json -Depth 10

if ($EnforceReadiness -and $failures.Count -gt 0) {
    exit 1
}
