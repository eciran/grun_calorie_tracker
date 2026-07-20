param(
    [int]$Port = 55433,
    [string]$PostgresImage = "postgres:latest"
)

$ErrorActionPreference = "Stop"
$containerName = "grun-product-benchmark-postgres"
$databaseName = "grun_product_benchmark"
$databaseUser = "benchmark"
$databasePassword = "benchmark-local-only"
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-DockerChecked {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
}

Push-Location $projectRoot
try {
    $existingContainer = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}"
    if ($LASTEXITCODE -ne 0) {
        throw "Docker is not available."
    }
    if ($existingContainer) {
        throw "Container '$containerName' already exists. Remove or rename it before running the benchmark."
    }

    $dockerRunArguments = @(
        "run", "--rm", "-d",
        "--name", $containerName,
        "-e", "POSTGRES_USER=$databaseUser",
        "-e", "POSTGRES_PASSWORD=$databasePassword",
        "-e", "POSTGRES_DB=$databaseName",
        "-p", "127.0.0.1:${Port}:5432",
        $PostgresImage
    )
    Invoke-DockerChecked -Arguments $dockerRunArguments | Out-Null

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw "Temporary PostgreSQL did not become ready."
    }

    $env:GRUN_RUN_POSTGRES_BENCHMARK = "true"
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$Port/$databaseName"
    $env:SPRING_DATASOURCE_USERNAME = $databaseUser
    $env:SPRING_DATASOURCE_PASSWORD = $databasePassword

    & .\mvnw.cmd "-Dtest=FoodProductImportPostgresPerformanceIntegrationTest" test
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL food import benchmark failed."
    }

    Write-Output "POSTGRES_FOOD_IMPORT_BENCHMARK_PASSED"
}
finally {
    $runningContainer = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}" 2>$null
    if ($runningContainer) {
        docker stop $containerName *> $null
    }
    Pop-Location
}