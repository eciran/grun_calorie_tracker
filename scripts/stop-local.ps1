$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot "src\main\resources\docker-compose.yml"

function Test-Command {
    param([string] $Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

if (-not (Test-Command "docker")) {
    Write-Error "Docker command was not found. Install Docker Desktop and make sure it is available in PATH."
}

try {
    docker info *> $null
} catch {
    Write-Error "Docker Desktop is not running. Start Docker Desktop if you want to stop Docker Compose services."
}

$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "Magellan1!"
$env:POSTGRES_DB = "grun_calorie_db"

Push-Location $projectRoot
try {
    Write-Host "Stopping Spring Boot processes started by Maven..."
    $javaProcesses = Get-CimInstance Win32_Process |
            Where-Object {
                $_.Name -eq "java.exe" -and
                (
                    $_.CommandLine -like "*grun-calorie-tracker*" -or
                    $_.CommandLine -like "*spring-boot:run*"
                )
            }

    foreach ($process in $javaProcesses) {
        Write-Host "Stopping process $($process.ProcessId)..."
        Stop-Process -Id $process.ProcessId -Force
    }

    Write-Host "Stopping PostgreSQL Docker Compose service..."
    docker compose -f $composeFile down

    Write-Host "Local GRun services stopped. PostgreSQL data volume was preserved."
} finally {
    Pop-Location
}
