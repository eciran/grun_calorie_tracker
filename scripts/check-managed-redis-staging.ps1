param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$AdminToken
)

$ErrorActionPreference = "Stop"

function Invoke-JsonGet([string]$Path, [hashtable]$Headers = @{}) {
    return Invoke-RestMethod -Method Get -Uri ($BaseUrl.TrimEnd("/") + $Path) -Headers $Headers -TimeoutSec 10
}

Write-Host "=== Managed Redis Staging Smoke ==="
Write-Host "Base URL: $BaseUrl"

$actuator = Invoke-JsonGet -Path "/actuator/health"
if ($actuator.status -ne "UP") {
    throw "Actuator health is $($actuator.status)."
}
Write-Host "- actuator health: UP"

$headers = @{ Authorization = "Bearer $AdminToken" }
$health = Invoke-JsonGet -Path "/api/v1/admin/system/health" -Headers $headers
Write-Host ("- application status: {0}" -f $health.status)
Write-Host ("- database: {0} ({1} ms)" -f $health.databaseStatus, $health.databaseLatencyMs)
Write-Host ("- redis: {0} ({1} ms)" -f $health.redisStatus, $health.redisLatencyMs)

if ($health.databaseStatus -ne "UP") {
    throw "Database health check failed: $($health.databaseStatus)."
}
if ($health.redisStatus -ne "UP") {
    throw "Managed Redis health check failed: $($health.redisStatus)."
}
if ([int64]$health.redisLatencyMs -gt 250) {
    throw "Managed Redis latency is above 250 ms: $($health.redisLatencyMs) ms."
}

Write-Host "Managed Redis staging smoke: PASS"
