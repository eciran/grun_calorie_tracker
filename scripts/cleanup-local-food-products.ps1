param(
    [string] $ContainerName = "grun-postgres"
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    $envFile = Join-Path (Split-Path -Parent $PSScriptRoot) ".env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return
    }

    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

Import-EnvFile

if (-not $env:POSTGRES_USER) { $env:POSTGRES_USER = "postgres" }
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "postgres" }
if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "grun_calorie_db" }

$sql = @"
BEGIN;

ALTER TABLE food_items
    ADD COLUMN IF NOT EXISTS market_region VARCHAR(16);

CREATE TEMP TABLE cleanup_food_item_ids AS
SELECT id
FROM food_items
WHERE COALESCE(is_custom, false) = false
  AND (market_region IS NULL OR market_region NOT IN ('IRL', 'TR', 'UK'));

DELETE FROM food_product_review_audits
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM user_favorites
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM meal_template_items
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM food_logs
WHERE food_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM food_items
WHERE id IN (SELECT id FROM cleanup_food_item_ids);

COMMIT;
"@

docker exec -i -e PGPASSWORD=$env:POSTGRES_PASSWORD $ContainerName `
    psql -h 127.0.0.1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -c $sql
