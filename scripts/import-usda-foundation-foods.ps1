param(
    [string] $ApiBaseUrl = "http://localhost:8080",
    [string] $ApiKey = $env:USDA_FOODDATA_API_KEY,
    [string] $QueryFile = ".\sample-data\usda-foundation-foods-queries.txt",
    [string] $OutputPath = ".\outputs\usda-foundation-foods-import.csv",
    [string] $Token = $env:GRUN_ADMIN_JWT,
    [int] $PageSize = 10,
    [int] $RequestDelayMs = 250,
    [switch] $SkipCleanup
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    $envFile = Join-Path (Get-Location) ".env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return
    }

    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

Import-EnvFile

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = $env:USDA_FOODDATA_API_KEY
}

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "USDA FoodData Central API key is required. Pass -ApiKey or set USDA_FOODDATA_API_KEY."
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    $adminEmail = $env:GRUN_LOCAL_ADMIN_EMAIL
    $adminPassword = $env:GRUN_LOCAL_ADMIN_PASSWORD
    if ([string]::IsNullOrWhiteSpace($adminEmail) -or [string]::IsNullOrWhiteSpace($adminPassword)) {
        throw "Admin JWT token is required. Pass -Token, set GRUN_ADMIN_JWT, or configure GRUN_LOCAL_ADMIN_EMAIL/GRUN_LOCAL_ADMIN_PASSWORD in .env."
    }

    $loginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    $Token = $login.token
}

$exportResult = powershell -ExecutionPolicy Bypass -File .\scripts\export-usda-fooddata-generic-products.ps1 `
    -ApiKey $ApiKey `
    -OutputPath $OutputPath `
    -QueryFile $QueryFile `
    -MarketRegion GLOBAL `
    -PageSize $PageSize `
    -RequestDelayMs $RequestDelayMs

$importResult = powershell -ExecutionPolicy Bypass -File .\scripts\import-food-pilot.ps1 `
    -ApiBaseUrl $ApiBaseUrl `
    -Token $Token `
    -FilePath $OutputPath `
    -ImportMode RAW_EXTERNAL `
    -ImportFormat USDA_FOODDATA

$cleanupResult = $null
if (-not $SkipCleanup) {
    $cleanupResult = powershell -ExecutionPolicy Bypass -File .\scripts\cleanup-local-food-products.ps1 -Apply
}

[pscustomobject] @{
    export = $exportResult | ConvertFrom-Json
    import = $importResult | ConvertFrom-Json
    cleanup = if ($cleanupResult) { $cleanupResult | ConvertFrom-Json } else { $null }
} | ConvertTo-Json -Depth 10