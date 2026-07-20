param(
    [string]$EnvPath = ".env"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location $projectRoot
try {
    . (Join-Path $projectRoot "scripts/load-env.ps1") -EnvPath $EnvPath

    if ([string]::IsNullOrWhiteSpace($env:GRUN_AI_OPENAI_API_KEY)) {
        throw "GRUN_AI_OPENAI_API_KEY is required."
    }
    if ($env:GRUN_AI_PROVIDER -ne "OPENAI") {
        throw "GRUN_AI_PROVIDER must be OPENAI for this smoke test."
    }

    & (Join-Path $projectRoot "mvnw.cmd") "-Dtest=OpenAiNutritionPlanLiveSmokeTest" "-Dgrun.live-ai-smoke=true" test
    if ($LASTEXITCODE -ne 0) {
        throw "AI nutrition live-provider smoke test failed."
    }

    Write-Output "AI_NUTRITION_LIVE_PROVIDER_SMOKE_PASSED"
    Write-Output "Requests sent: 3 (GENERAL, WORKOUT_ALIGNED, PREPARATION_GUIDE)"
}
finally {
    Pop-Location
}
