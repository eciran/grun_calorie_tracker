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
    [switch] $SkipReport
)

$ErrorActionPreference = "Stop"

function Resolve-BatchOutputPath {
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        return $OutputPath
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    return ".\outputs\food-catalog-$($MarketRegion.ToLowerInvariant())-$Limit-$timestamp.tsv"
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

[pscustomobject] $summary | ConvertTo-Json -Depth 8
