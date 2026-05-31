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
    Write-Error "Docker Desktop is not running. Start Docker Desktop, then run this script again."
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
    Write-Host "Starting PostgreSQL and Redis with Docker Compose..."
    docker compose -f $composeFile up -d

    Write-Host "Waiting for PostgreSQL to become ready..."
    $ready = $false
    for ($i = 1; $i -le 30; $i++) {
        docker exec grun-postgres pg_isready -U $env:POSTGRES_USER -d $env:POSTGRES_DB *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        Write-Error "PostgreSQL did not become ready in time. Check container logs with: docker logs grun-postgres"
    }

    Write-Host "Waiting for Redis to become ready..."
    $redisReady = $false
    for ($i = 1; $i -le 30; $i++) {
        docker exec grun-redis redis-cli ping *> $null
        if ($LASTEXITCODE -eq 0) {
            $redisReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }

    if (-not $redisReady) {
        Write-Error "Redis did not become ready in time. Check container logs with: docker logs grun-redis"
    }

    Write-Host "PostgreSQL and Redis are ready. Starting Spring Boot API..."
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
