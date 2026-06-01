param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminToken = "",
    [string]$EnvPath = ".env",
    [switch]$SkipEnvLoad,
    [switch]$Production
)

$ErrorActionPreference = "Stop"

$loadEnvScript = Join-Path $PSScriptRoot "load-env.ps1"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnvPath = if ([System.IO.Path]::IsPathRooted($EnvPath)) { $EnvPath } else { Join-Path $projectRoot $EnvPath }
if (-not $SkipEnvLoad -and (Test-Path -LiteralPath $loadEnvScript) -and (Test-Path -LiteralPath $resolvedEnvPath)) {
    . $loadEnvScript -EnvPath $resolvedEnvPath
}

function Check-Env([string]$Name, [bool]$Required = $true) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ($Required -and [string]::IsNullOrWhiteSpace($value)) {
        return [PSCustomObject]@{ Name = $Name; Ok = $false; Detail = "missing" }
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        return [PSCustomObject]@{ Name = $Name; Ok = $true; Detail = "not-set (optional)" }
    }
    return [PSCustomObject]@{ Name = $Name; Ok = $true; Detail = "set" }
}

function Check-EnvEquals([string]$Name, [string]$Expected) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return [PSCustomObject]@{ Name = $Name; Ok = $false; Detail = "missing" }
    }
    if ($value -ne $Expected) {
        return [PSCustomObject]@{ Name = $Name; Ok = $false; Detail = "expected $Expected, got $value" }
    }
    return [PSCustomObject]@{ Name = $Name; Ok = $true; Detail = "set to $Expected" }
}

function Check-EnvContains([string]$Name, [string]$Expected) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return [PSCustomObject]@{ Name = $Name; Ok = $false; Detail = "missing" }
    }
    $items = $value.Split(",") | ForEach-Object { $_.Trim() }
    if ($items -notcontains $Expected) {
        return [PSCustomObject]@{ Name = $Name; Ok = $false; Detail = "must include $Expected, got $value" }
    }
    return [PSCustomObject]@{ Name = $Name; Ok = $true; Detail = "includes $Expected" }
}

function Invoke-AdminGet([string]$Path, [string]$Token) {
    $headers = @{
        Authorization = "Bearer $Token"
    }
    return Invoke-RestMethod -Method Get -Uri ($BaseUrl + $Path) -Headers $headers
}

Write-Host "=== Production Gate Check ==="
Write-Host "Base URL: $BaseUrl"

$secretChecks = @(
    (Check-Env "JWT_SECRET"),
    (Check-Env "SPRING_DATASOURCE_URL"),
    (Check-Env "POSTGRES_USER"),
    (Check-Env "POSTGRES_PASSWORD"),
    (Check-Env "POSTGRES_DB"),
    (Check-Env "GRUN_REVENUECAT_WEBHOOK_AUTHORIZATION"),
    (Check-Env "GRUN_BREVO_API_KEY"),
    (Check-Env "GRUN_MAIL_PROVIDER"),
    (Check-Env "GRUN_MAIL_FROM_EMAIL")
)

if ($Production) {
    $secretChecks += @(
        (Check-EnvContains "SPRING_PROFILES_ACTIVE" "prod"),
        (Check-EnvEquals "GRUN_MAIL_PROVIDER" "BREVO"),
        (Check-EnvEquals "GRUN_REVENUECAT_STRICT_PRODUCT_MAPPING" "true"),
        (Check-EnvEquals "GRUN_RATE_LIMIT_ENABLED" "true"),
        (Check-EnvEquals "GRUN_RATE_LIMIT_REDIS_ENABLED" "true"),
        (Check-EnvEquals "GRUN_ERRORS_INCLUDE_INTERNAL_DETAILS" "false"),
        (Check-EnvEquals "GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED" "false"),
        (Check-EnvEquals "GRUN_LOCAL_DEMO_SEED_ENABLED" "false"),
        (Check-Env "SPRING_DATA_REDIS_HOST"),
        (Check-Env "SPRING_DATA_REDIS_PORT")
    )
}

$failedSecrets = $secretChecks | Where-Object { -not $_.Ok }
$apiFailures = @()

Write-Host ""
Write-Host "[Environment]"
foreach ($check in $secretChecks) {
    $status = if ($check.Ok) { "OK" } else { "FAIL" }
    Write-Host ("- {0}: {1} ({2})" -f $check.Name, $status, $check.Detail)
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    Write-Host ""
    Write-Host "Admin API checks skipped: AdminToken not provided."
} else {
    Write-Host ""
    Write-Host "[Admin API]"
    try {
        $health = Invoke-AdminGet -Path "/api/v1/admin/system/health" -Token $AdminToken
        Write-Host "- /admin/system/health: OK"
        Write-Host ("  status={0}, databaseStatus={1}, failedRevenueCatEvents={2}, systemAlertsLast24h={3}" -f $health.status, $health.databaseStatus, $health.failedRevenueCatEvents, $health.systemAlertsLast24h)
        if ($health.status -ne "UP") {
            $apiFailures += "Admin health status is $($health.status)"
        }
        if ($health.databaseStatus -ne "UP") {
            $apiFailures += "Database status is $($health.databaseStatus)"
        }
        if ([int64]$health.failedRevenueCatEvents -gt 0) {
            $apiFailures += "RevenueCat has failed provider events"
        }
        if ([int64]$health.systemAlertsLast24h -gt 0) {
            $apiFailures += "System alerts were created in the last 24 hours"
        }
        if ($health.warnings -and $health.warnings.Count -gt 0) {
            $apiFailures += "Admin health has warnings"
        }
    } catch {
        Write-Host "- /admin/system/health: FAIL"
        Write-Host ("  " + $_.Exception.Message)
        $apiFailures += "Admin health endpoint failed"
    }

    try {
        $config = Invoke-AdminGet -Path "/api/v1/admin/revenuecat/config" -Token $AdminToken
        Write-Host "- /admin/revenuecat/config: OK"
        Write-Host ("  productionReady={0}, strictProductMapping={1}, webhookConfigured={2}" -f $config.productionReady, $config.strictProductMapping, $config.webhookAuthorizationConfigured)
        if ($config.missingRequiredConfig) {
            Write-Host ("  missingRequiredConfig=" + (($config.missingRequiredConfig -join ", ")))
        }
        if ($config.warnings) {
            Write-Host ("  warnings=" + (($config.warnings -join ", ")))
        }
        if ($config.productionReady -ne $true) {
            $apiFailures += "RevenueCat configuration is not production ready"
        }
        if ($config.strictProductMapping -ne $true) {
            $apiFailures += "RevenueCat strict product mapping is disabled"
        }
        if ($config.webhookAuthorizationConfigured -ne $true) {
            $apiFailures += "RevenueCat webhook authorization is not configured"
        }
    } catch {
        Write-Host "- /admin/revenuecat/config: FAIL"
        Write-Host ("  " + $_.Exception.Message)
        $apiFailures += "RevenueCat config endpoint failed"
    }
}

Write-Host ""
if ($failedSecrets.Count -gt 0) {
    Write-Host "Gate result: FAIL (missing required environment values)"
    exit 1
}

if ($apiFailures.Count -gt 0) {
    Write-Host "Gate result: FAIL (admin API checks failed)"
    foreach ($failure in $apiFailures) {
        Write-Host ("- " + $failure)
    }
    exit 1
}

Write-Host "Gate result: PASS"
exit 0
