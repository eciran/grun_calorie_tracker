param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminToken = "",
    [string]$PlusProductId = "grun_plus_monthly",
    [string]$ProProductId = "grun_pro_monthly",
    [string]$AiAddonProductId = "grun_ai_15_credits"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    throw "AdminToken is required. Pass a valid admin JWT with -AdminToken."
}

function Invoke-AdminJsonPost([string]$Path, [hashtable]$Body) {
    $headers = @{
        Authorization = "Bearer $AdminToken"
    }
    return Invoke-RestMethod -Method Post -Uri ($BaseUrl + $Path) -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10)
}

function Invoke-AdminGet([string]$Path) {
    $headers = @{
        Authorization = "Bearer $AdminToken"
    }
    return Invoke-RestMethod -Method Get -Uri ($BaseUrl + $Path) -Headers $headers
}

Write-Host "=== RevenueCat Configuration Smoke Test ==="
Write-Host "Base URL: $BaseUrl"

$config = Invoke-AdminGet -Path "/api/v1/admin/revenuecat/config"
Write-Host ("Config: productionReady={0}, webhookConfigured={1}, strictProductMapping={2}" -f $config.productionReady, $config.webhookAuthorizationConfigured, $config.strictProductMapping)
if ($config.missingRequiredConfig -and $config.missingRequiredConfig.Count -gt 0) {
    throw "RevenueCat config has missing required values: $($config.missingRequiredConfig -join ', ')"
}

$checks = @(
    @{ Name = "PLUS monthly"; Body = @{ eventType = "INITIAL_PURCHASE"; productId = $PlusProductId; entitlementIds = @("plus") }; ExpectedPlan = "PLUS"; ExpectedAddon = $null },
    @{ Name = "PRO monthly"; Body = @{ eventType = "INITIAL_PURCHASE"; productId = $ProProductId; entitlementIds = @("pro") }; ExpectedPlan = "PRO"; ExpectedAddon = $null },
    @{ Name = "AI add-on"; Body = @{ eventType = "NON_RENEWING_PURCHASE"; productId = $AiAddonProductId; entitlementIds = @() }; ExpectedPlan = $null; ExpectedAddon = 15 }
)

foreach ($check in $checks) {
    $result = Invoke-AdminJsonPost -Path "/api/v1/admin/revenuecat/mapping/validate" -Body $check.Body
    Write-Host ("{0}: recognized={1}, mappingType={2}, plan={3}, addonQuota={4}" -f $check.Name, $result.recognized, $result.mappingType, $result.subscriptionPlan, $result.aiAddonQuota)
    if (-not $result.recognized) {
        throw "$($check.Name) mapping is invalid: $($result.message)"
    }
    if ($check.ExpectedPlan -and $result.subscriptionPlan -ne $check.ExpectedPlan) {
        throw "$($check.Name) expected plan $($check.ExpectedPlan), got $($result.subscriptionPlan)"
    }
    if ($check.ExpectedAddon -and $result.aiAddonQuota -ne $check.ExpectedAddon) {
        throw "$($check.Name) expected add-on quota $($check.ExpectedAddon), got $($result.aiAddonQuota)"
    }
}

Write-Host "RevenueCat configuration smoke test passed."
