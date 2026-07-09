param(
    [string] $ContainerName = "grun-postgres",
    [switch] $Apply
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

function Invoke-LocalFoodSql {
    param([string] $Sql)

    docker exec -i -e PGPASSWORD=$env:POSTGRES_PASSWORD $ContainerName `
        psql -h 127.0.0.1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -t -A -F "|" -c $Sql
}

Import-EnvFile

if (-not $env:POSTGRES_USER) { $env:POSTGRES_USER = "postgres" }
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "postgres" }
if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "grun_calorie_db" }

$candidateWhere = @"
f.market_region is null
or f.verification_status = 'REJECTED'
or f.calories is null
or f.protein is null
or f.fat is null
or f.carbs is null
or f.calories < 0
or f.calories > 1000
or (
    f.protein is not null and f.fat is not null and f.carbs is not null
    and (f.protein + f.fat + f.carbs) > 120
)
or exists (
    select 1
    from food_product_quality_issues q
    where q.food_item_id = f.id
      and q.resolved = false
      and q.issue_type in (
        'MISSING_CALORIES',
        'MISSING_MACROS',
        'SUSPICIOUS_CALORIES',
        'SUSPICIOUS_MACROS',
      )
)
"@

$summarySql = @"
with cleanup_food_item_ids as (
    select distinct f.id
    from food_items f
    where $candidateWhere
)
select 'cleanup_candidates', count(*) from cleanup_food_item_ids
union all
select 'referenced_food_logs', count(*) from food_logs where food_id in (select id from cleanup_food_item_ids)
union all
select 'referenced_favorites', count(*) from user_favorites where food_item_id in (select id from cleanup_food_item_ids)
union all
select 'referenced_meal_template_items', count(*) from meal_template_items where food_item_id in (select id from cleanup_food_item_ids)
union all
select 'referenced_meal_plan_items', count(*) from meal_plan_items where food_item_id in (select id from cleanup_food_item_ids)
union all
select 'referenced_recipe_ingredients', count(*) from recipe_ingredients where food_item_id in (select id from cleanup_food_item_ids)
union all
select 'referenced_review_audits', count(*) from food_product_review_audits where food_item_id in (select id from cleanup_food_item_ids);
"@

$summary = Invoke-LocalFoodSql -Sql $summarySql

if (-not $Apply) {
    [pscustomobject] @{
        mode = "dry-run"
        applyCommand = "powershell -ExecutionPolicy Bypass -File .\scripts\cleanup-local-food-products.ps1 -Apply"
        summary = $summary
    } | ConvertTo-Json -Depth 4
    return
}

$cleanupSql = @"
BEGIN;

CREATE TEMP TABLE cleanup_food_item_ids AS
select distinct f.id
from food_items f
where $candidateWhere;

DELETE FROM food_product_review_audits
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM user_favorites
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM meal_template_items
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM meal_plan_items
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM recipe_ingredients
WHERE food_item_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM food_logs
WHERE food_id IN (SELECT id FROM cleanup_food_item_ids);

DELETE FROM food_items
WHERE id IN (SELECT id FROM cleanup_food_item_ids);

COMMIT;
"@

Invoke-LocalFoodSql -Sql $cleanupSql | Out-Null

[pscustomobject] @{
    mode = "applied"
    before = $summary
} | ConvertTo-Json -Depth 4
