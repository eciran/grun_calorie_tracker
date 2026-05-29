param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminToken = ""
)

$ErrorActionPreference = "Stop"

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

$failedSecrets = $secretChecks | Where-Object { -not $_.Ok }

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
        Write-Host ("  appStatus={0}, databaseStatus={1}, failedRevenueCatEvents={2}" -f $health.appStatus, $health.databaseStatus, $health.failedRevenueCatEvents)
    } catch {
        Write-Host "- /admin/system/health: FAIL"
        Write-Host ("  " + $_.Exception.Message)
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
    } catch {
        Write-Host "- /admin/revenuecat/config: FAIL"
        Write-Host ("  " + $_.Exception.Message)
    }
}

Write-Host ""
if ($failedSecrets.Count -gt 0) {
    Write-Host "Gate result: FAIL (missing required environment values)"
    exit 1
}

Write-Host "Gate result: ENV OK (API checks may still need manual confirmation)"
exit 0
