param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminToken = "",
    [string]$EnvPath = ".env",
    [switch]$SkipEnvLoad,
    [switch]$Production,
    [switch]$RunOnboardingTests,
    [switch]$RequireOnboardingEvents
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

$onboardingTestsFailed = $false
if ($RunOnboardingTests) {
    Write-Host ""
    Write-Host "[Onboarding automated tests]"
    Push-Location $projectRoot
    try {
        & .\mvnw.cmd "-Dtest=AuthControllerTest,FederatedAuthServiceImplTest,LocaleConfigTest,LocalizationContractTest,UserGoalServiceImplTest,OnboardingServiceImplTest,OnboardingControllerTest,OnboardingAnalyticsServiceImplTest,AdminOnboardingAnalyticsServiceImplTest,AdminOnboardingAnalyticsControllerTest,ProductAnalyticsOnboardingPrivacyTest,MobileApiContractTest" test
        if ($LASTEXITCODE -ne 0) {
            $onboardingTestsFailed = $true
            Write-Host "- onboarding test suite: FAIL"
        } else {
            Write-Host "- onboarding test suite: OK"
        }
    } finally {
        Pop-Location
    }
}

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
        (Check-EnvEquals "SPRING_CACHE_TYPE" "redis"),
        (Check-EnvEquals "SPRING_DATA_REDIS_SSL_ENABLED" "true"),
        (Check-EnvEquals "GRUN_ERRORS_INCLUDE_INTERNAL_DETAILS" "false"),
        (Check-Env "GRUN_AI_ENABLED"),
        (Check-Env "GRUN_AI_PROVIDER"),
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
        Write-Host ("  status={0}, databaseStatus={1}, redisStatus={2}, redisLatencyMs={3}, failedRevenueCatEvents={4}, systemAlertsLast24h={5}, aiEnabled={6}, aiProvider={7}" -f $health.status, $health.databaseStatus, $health.redisStatus, $health.redisLatencyMs, $health.failedRevenueCatEvents, $health.systemAlertsLast24h, $health.aiEnabled, $health.aiProvider)
        Write-Host ("  cacheHits={0}, cacheMisses={1}, cacheErrors={2}, cacheHitRate={3}" -f $health.analyticsCacheHits, $health.analyticsCacheMisses, $health.analyticsCacheErrors, $health.analyticsCacheHitRate)
        if ($health.status -ne "UP") {
            $apiFailures += "Admin health status is $($health.status)"
        }
        if ($health.databaseStatus -ne "UP") {
            $apiFailures += "Database status is $($health.databaseStatus)"
        }
        if ($health.redisStatus -ne "UP") {
            $apiFailures += "Redis status is $($health.redisStatus)"
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
        $onboarding = Invoke-AdminGet -Path "/api/v1/admin/onboarding/analytics?hours=168" -Token $AdminToken
        $onboardingTotal = [int64]$onboarding.started + [int64]$onboarding.stepViewed + [int64]$onboarding.stepCompleted + [int64]$onboarding.stepFailed + [int64]$onboarding.resumed + [int64]$onboarding.previewed + [int64]$onboarding.completed + [int64]$onboarding.abandoned
        Write-Host "- /admin/onboarding/analytics: OK"
        Write-Host ("  started={0}, resumed={1}, previewed={2}, completed={3}, abandoned={4}, totalEvents={5}" -f $onboarding.started, $onboarding.resumed, $onboarding.previewed, $onboarding.completed, $onboarding.abandoned, $onboardingTotal)
        if ($RequireOnboardingEvents -and $onboardingTotal -eq 0) {
            $apiFailures += "No onboarding funnel events were found in the last 168 hours"
        }
    } catch {
        Write-Host "- /admin/onboarding/analytics: FAIL"
        Write-Host ("  " + $_.Exception.Message)
        $apiFailures += "Onboarding analytics endpoint failed"
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
if ($onboardingTestsFailed) {
    Write-Host "Gate result: FAIL (onboarding automated tests failed)"
    exit 1
}
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
