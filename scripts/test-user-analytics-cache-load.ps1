param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$Token,
    [int]$Requests = 200,
    [int]$Concurrency = 20,
    [int]$P95BudgetMs = 300,
    [double]$MaxErrorRate = 0,
    [string]$Paths = "",
    [string]$ReportPath = "target/reports/user-analytics-cache-load.json"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot "load-user-analytics-cache.mjs"
$previousToken = $env:GRUN_LOAD_TEST_TOKEN
try {
    $env:GRUN_LOAD_TEST_TOKEN = $Token
    Push-Location $projectRoot
    $arguments = @(
        $runner,
        "--base-url", $BaseUrl,
        "--requests", $Requests,
        "--concurrency", $Concurrency,
        "--p95-budget-ms", $P95BudgetMs,
        "--max-error-rate", $MaxErrorRate,
        "--report", $ReportPath
    )
    if (-not [string]::IsNullOrWhiteSpace($Paths)) {
        $arguments += @("--paths", $Paths)
    }
    & node @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "User analytics cache load gate failed."
    }
} finally {
    Pop-Location
    $env:GRUN_LOAD_TEST_TOKEN = $previousToken
}