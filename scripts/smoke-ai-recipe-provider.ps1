param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "",
    [string]$Password = "StrongPass1!",
    [string]$EnvPath = ".env",
    [switch]$SkipEnvLoad
)

$ErrorActionPreference = "Stop"

$loadEnvScript = Join-Path $PSScriptRoot "load-env.ps1"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnvPath = if ([System.IO.Path]::IsPathRooted($EnvPath)) { $EnvPath } else { Join-Path $projectRoot $EnvPath }
if (-not $SkipEnvLoad -and (Test-Path -LiteralPath $loadEnvScript) -and (Test-Path -LiteralPath $resolvedEnvPath)) {
    . $loadEnvScript -EnvPath $resolvedEnvPath
}

function Require-Env([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required env is missing: $Name"
    }
    return $value
}

function Invoke-JsonPost([string]$Url, [hashtable]$Body, [string]$Token = "") {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }
    return Invoke-RestMethod -Method Post -Uri $Url -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 20)
}

Write-Host "=== AI Recipe Provider Smoke Test ==="

$provider = Require-Env "GRUN_AI_PROVIDER"
$model = Require-Env "GRUN_AI_MODEL"
if ($provider -eq "DISABLED") {
    throw "GRUN_AI_PROVIDER must not be DISABLED."
}

Write-Host "Base URL: $BaseUrl"
Write-Host "Provider: $provider"
Write-Host "Model: $model"

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = "ai-recipe-smoke-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + "@example.com"
}

$authBody = @{
    email = $Email
    password = $Password
}

try {
    $auth = Invoke-JsonPost -Url "$BaseUrl/api/v1/auth/login" -Body $authBody
    Write-Host "Login: OK"
} catch {
    Write-Host "Login failed, trying register for smoke user."
    $auth = Invoke-JsonPost -Url "$BaseUrl/api/v1/auth/register" -Body $authBody
    Write-Host "Register: OK"
}

$token = $auth.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Auth response did not include token."
}

$request = @{
    prompt = "High protein dinner with chicken, rice, and yoghurt. Keep it practical for a mobile food tracker."
    mealType = "DINNER"
    marketRegion = "TR"
    language = "en"
    servingCount = 2
    targetCaloriesPerServing = 650
    dietaryPreferences = @("HIGH_PROTEIN")
    excludedIngredients = @()
    availableIngredients = @("chicken breast", "rice", "yoghurt")
}

$draft = Invoke-JsonPost -Url "$BaseUrl/api/v1/ai/recipes/generate" -Body $request -Token $token

Write-Host "AI recipe generation: OK"
Write-Host "Request id: $($draft.requestId)"
Write-Host "Status: $($draft.status)"
Write-Host "Suggested recipe: $($draft.suggestedRecipe.name)"
Write-Host "Suggested ingredients: $($draft.suggestedIngredients.Count)"
Write-Host "Remaining AI quota: $($draft.aiRemainingThisPeriod)"
Write-Host ""
Write-Host "Manual check required:"
Write-Host "1. Confirm generated ingredients are mapped or marked reviewRequired."
Write-Host "2. Confirm no secret/provider key is printed in logs."
Write-Host "3. Confirm user must still review/confirm before recipe persistence."
