$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
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

$composeFile = Join-Path $projectRoot "src\main\resources\docker-compose.yml"

Push-Location $projectRoot
try {
    docker compose -f $composeFile up -d

    $sql = @"
DELETE FROM exercise_logs
WHERE user_id IN (SELECT id FROM users WHERE email = 'demo.user@grun.local')
   OR source = 'LOCAL_DEMO';

DELETE FROM food_logs
WHERE user_id IN (SELECT id FROM users WHERE email = 'demo.user@grun.local');

DELETE FROM food_product_review_audits
WHERE food_item_id IN (
    SELECT id FROM food_items
    WHERE normalized_barcode IN ('8690000000011', '8690000000028', '8690000000035', '8690000000042')
);

DELETE FROM food_items
WHERE normalized_barcode IN ('8690000000011', '8690000000028', '8690000000035', '8690000000042');

DELETE FROM users
WHERE email = 'demo.user@grun.local';
"@

    docker exec -i grun-postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -c $sql
    Write-Host "Local demo user, demo products, demo logs, and related demo audits were removed."
    Write-Host "Admin bootstrap user was preserved."
} finally {
    Pop-Location
}
