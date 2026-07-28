param()

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot "src\main\resources\docker-compose.yml"
$envFile = Join-Path $projectRoot ".env"

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

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$($env:POSTGRES_DB)"
$env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
$env:GRUN_RUN_REDIS_INTEGRATION_TESTS = "true"
$env:GRUN_LOCAL_ADMIN_EMAIL = "cache-integration@grun.local"
$env:GRUN_LOCAL_ADMIN_PASSWORD = "CacheIntegrationPass1!"

Push-Location $projectRoot
try {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop is not running."
    }

    docker compose -f $composeFile up -d postgres redis
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL and Redis containers could not be started."
    }

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec grun-postgres pg_isready -U $env:POSTGRES_USER -d $env:POSTGRES_DB *> $null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 30) { throw "PostgreSQL did not become ready." }
        Start-Sleep -Seconds 1
    }

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec grun-redis redis-cli ping *> $null
        if ($LASTEXITCODE -eq 0) { break }
        if ($attempt -eq 30) { throw "Redis did not become ready." }
        Start-Sleep -Seconds 1
    }

    .\mvnw.cmd "-Dtest=UserAnalyticsCachePostgresRedisIntegrationTest" test
    if ($LASTEXITCODE -ne 0) {
        throw "User analytics cache integration tests failed."
    }
} finally {
    Pop-Location
}
