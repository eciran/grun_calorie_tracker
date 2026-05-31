$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot "src\main\resources\docker-compose.yml"
$envFile = Join-Path $projectRoot ".env"

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

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

if (-not $env:POSTGRES_USER) { $env:POSTGRES_USER = "postgres" }
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "postgres" }
if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "grun_calorie_db" }

Push-Location $projectRoot
try {
    Write-Host "Stopping Spring Boot processes started by Maven..."
    $javaProcesses = @()

    try {
        $javaProcesses = Get-CimInstance Win32_Process |
                Where-Object {
                    $_.Name -eq "java.exe" -and
                    (
                        $_.CommandLine -like "*grun-calorie-tracker*" -or
                        $_.CommandLine -like "*spring-boot:run*"
                    )
                }
    } catch {
        Write-Host "Could not inspect Java command lines. Falling back to process listening on port 8080..."
        $portLines = netstat -ano | Select-String ":8080" | Select-String "LISTENING"
        foreach ($line in $portLines) {
            $parts = ($line.ToString() -split "\s+") | Where-Object { $_ }
            $pidValue = $parts[-1]
            if ($pidValue -match "^\d+$") {
                $process = Get-Process -Id ([int] $pidValue) -ErrorAction SilentlyContinue
                if ($process) {
                    $javaProcesses += $process
                }
            }
        }
    }

    foreach ($process in $javaProcesses) {
        $processId = if ($process.ProcessId) { $process.ProcessId } else { $process.Id }
        Write-Host "Stopping process $processId..."
        Stop-Process -Id $processId -Force
    }

    Write-Host "Stopping PostgreSQL and Redis Docker Compose services..."
    docker compose -f $composeFile down

    Write-Host "Local GRun services stopped. PostgreSQL and Redis data volumes were preserved."
} finally {
    Pop-Location
}
