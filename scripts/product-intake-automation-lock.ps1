param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Acquire", "Release", "Status")]
    [string]$Action,

    [string]$RunId,

    [ValidateRange(30, 240)]
    [int]$StaleAfterMinutes = 90
)

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$lockDirectory = Join-Path $workspace "tmp\product-intake-automation"
$lockPath = Join-Path $lockDirectory "run.lock.json"

if (-not $lockPath.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Automation lock path escaped the workspace."
}

function Read-Lock {
    if (-not (Test-Path -LiteralPath $lockPath)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $lockPath -Raw | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{
            runId = "unreadable"
            startedAt = $null
            branch = $null
        }
    }
}

function Write-Result([hashtable]$Value) {
    $Value | ConvertTo-Json -Compress
}

if ($Action -eq "Status") {
    $existing = Read-Lock
    if ($null -eq $existing) {
        Write-Result @{ status = "FREE"; lockPath = $lockPath }
        exit 0
    }
    Write-Result @{
        status = "LOCKED"
        lockPath = $lockPath
        runId = $existing.runId
        startedAt = $existing.startedAt
        branch = $existing.branch
    }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($RunId)) {
    throw "RunId is required for Acquire and Release."
}

if ($Action -eq "Release") {
    $existing = Read-Lock
    if ($null -eq $existing) {
        Write-Result @{ status = "ALREADY_FREE"; runId = $RunId }
        exit 0
    }
    if ($existing.runId -ne $RunId) {
        Write-Result @{
            status = "NOT_OWNER"
            requestedRunId = $RunId
            ownerRunId = $existing.runId
        }
        exit 4
    }
    Remove-Item -LiteralPath $lockPath -Force
    Write-Result @{ status = "RELEASED"; runId = $RunId }
    exit 0
}

New-Item -ItemType Directory -Path $lockDirectory -Force | Out-Null
$existing = Read-Lock
if ($null -ne $existing) {
    $startedAt = $null
    if ($existing.startedAt) {
        try {
            $startedAt = [DateTimeOffset]::Parse($existing.startedAt)
        } catch {
            $startedAt = $null
        }
    }
    $isStale = $null -eq $startedAt -or
        $startedAt -lt [DateTimeOffset]::UtcNow.AddMinutes(-$StaleAfterMinutes)
    if (-not $isStale) {
        Write-Result @{
            status = "BUSY"
            ownerRunId = $existing.runId
            startedAt = $existing.startedAt
            branch = $existing.branch
        }
        exit 3
    }

    $archiveName = "run.lock.stale.{0}.{1}.json" -f
        ([DateTimeOffset]::UtcNow.ToString("yyyyMMddTHHmmssZ")),
        ([Guid]::NewGuid().ToString("N"))
    Move-Item -LiteralPath $lockPath -Destination (Join-Path $lockDirectory $archiveName)
}

$branch = (& git -C $workspace branch --show-current).Trim()
$payload = @{
    runId = $RunId
    startedAt = [DateTimeOffset]::UtcNow.ToString("O")
    branch = $branch
    processId = $PID
}
$json = $payload | ConvertTo-Json -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

try {
    $stream = [System.IO.File]::Open(
        $lockPath,
        [System.IO.FileMode]::CreateNew,
        [System.IO.FileAccess]::Write,
        [System.IO.FileShare]::None
    )
    try {
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }
} catch [System.IO.IOException] {
    Write-Result @{ status = "BUSY_RACE"; runId = $RunId }
    exit 3
}

Write-Result @{
    status = "ACQUIRED"
    runId = $RunId
    branch = $branch
    lockPath = $lockPath
}
