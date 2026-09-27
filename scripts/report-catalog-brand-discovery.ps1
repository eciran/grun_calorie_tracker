param(
    [string] $OutputDirectory = ".\outputs\catalog-brand-discovery",
    [string] $ContainerName = "grun-postgres",
    [string] $DatabaseHost = "",
    [int] $DatabasePort = 5432,
    [string] $DatabaseName = "",
    [string] $DatabaseUser = "",
    [string] $DatabasePassword = "",
    [int] $SampleLimit = 10
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    $envFile = Join-Path (Get-Location) ".env"
    if (-not (Test-Path -LiteralPath $envFile)) { return }
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

function Invoke-ReadOnlyPsql {
    param([string] $Sql, [switch] $Csv)

    $prefix = "set default_transaction_read_only = on; set statement_timeout = '15min'; "
    $command = $prefix + $Sql
    $psqlArgs = @("-v", "ON_ERROR_STOP=1", "-X", "-q")
    if ($Csv) { $psqlArgs += "--csv" } else { $psqlArgs += @("-t", "-A") }
    $psqlArgs += @("-c", $command)

    if ($DatabaseHost) {
        $env:PGPASSWORD = $DatabasePassword
        $result = & psql -h $DatabaseHost -p $DatabasePort -U $DatabaseUser -d $DatabaseName @psqlArgs
    } else {
        $result = & docker exec -e "PGPASSWORD=$DatabasePassword" $ContainerName `
            psql -h 127.0.0.1 -U $DatabaseUser -d $DatabaseName @psqlArgs
    }
    if ($LASTEXITCODE -ne 0) { throw "Read-only catalog query failed with exit code $LASTEXITCODE." }
    return $result
}

function Write-Utf8Lines {
    param([string] $Path, [object] $Value)
    $parent = Split-Path -Parent $Path
    if ($parent) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    [IO.File]::WriteAllLines($Path, [string[]]@($Value), [Text.UTF8Encoding]::new($false))
}

Import-EnvFile
if (-not $DatabaseName) { $DatabaseName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "grun_calorie_db" } }
if (-not $DatabaseUser) { $DatabaseUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "postgres" } }
if (-not $DatabasePassword) { $DatabasePassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres" } }
if ($SampleLimit -lt 1 -or $SampleLimit -gt 50) { throw "SampleLimit must be between 1 and 50." }

$resolvedOutput = [IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDirectory = Join-Path $resolvedOutput $stamp
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

# Must stay equivalent to FoodBrandSearchRules.key. chr() keeps Windows PowerShell 5 parsing ASCII-safe.
$normalizedExpression = "lower(replace(replace(replace(replace(replace(replace(replace(replace(replace(brand, chr(32), ''), chr(45), ''), chr(8226), ''), chr(183), ''), chr(8208), ''), chr(8209), ''), chr(8211), ''), chr(8212), ''), chr(160), ''))"
$basePredicate = "brand is not null and trim(brand) <> ''"

$summarySql = @"
select json_build_object(
  'totalProducts', count(*),
  'productsWithBrand', count(*) filter (where $basePredicate),
  'distinctRawBrands', count(distinct trim(brand)) filter (where $basePredicate),
  'distinctNormalizedKeys', count(distinct $normalizedExpression) filter (where $basePredicate),
  'brandedProductsMissingBrand', count(*) filter (
      where catalog_type = 'BRANDED_PRODUCT' and (brand is null or trim(brand) = '')
  )
) from food_items;
"@

$groupsSql = @"
with normalized as (
  select trim(brand) raw_brand, $normalizedExpression normalized_key,
         count(*) product_count,
         count(*) filter (where catalog_type = 'BRANDED_PRODUCT') branded_product_count,
         count(distinct data_source) source_count,
         string_agg(distinct data_source::text, ';' order by data_source::text) sources
  from food_items
  where $basePredicate
  group by trim(brand), $normalizedExpression
), ranked as (
  select *, row_number() over (
      partition by normalized_key order by product_count desc, length(raw_brand), raw_brand
  ) display_rank
  from normalized
), grouped as (
  select normalized_key,
         max(raw_brand) filter (where display_rank = 1) proposed_canonical_name,
         sum(product_count) product_count,
         sum(branded_product_count) branded_product_count,
         count(*) spelling_count,
         string_agg(raw_brand || ' [' || product_count || ']', ' | ' order by product_count desc, raw_brand) spellings,
         string_agg(distinct sources, ';' order by sources) source_evidence
  from ranked
  group by normalized_key
)
select normalized_key, proposed_canonical_name, product_count, branded_product_count,
       spelling_count, (spelling_count > 1) review_required, spellings, source_evidence
from grouped
order by product_count desc, normalized_key;
"@

$missingSql = @"
select id, name, display_name, barcode, data_source, market_region, verification_status
from food_items
where catalog_type = 'BRANDED_PRODUCT' and (brand is null or trim(brand) = '')
order by coalesce(usage_count, 0) desc, id
limit 5000;
"@

$summaryJson = Invoke-ReadOnlyPsql -Sql $summarySql
$groupsCsv = Invoke-ReadOnlyPsql -Sql $groupsSql -Csv
$missingCsv = Invoke-ReadOnlyPsql -Sql $missingSql -Csv

$groupsPath = Join-Path $runDirectory "brand-groups.csv"
$missingPath = Join-Path $runDirectory "branded-products-missing-brand-sample.csv"
Write-Utf8Lines -Path $groupsPath -Value $groupsCsv
Write-Utf8Lines -Path $missingPath -Value $missingCsv

$groupRows = Import-Csv -LiteralPath $groupsPath
$reviewRows = @($groupRows | Where-Object { $_.review_required -eq "t" })
$reviewPath = Join-Path $runDirectory "brand-normalization-review.csv"
$reviewRows | Export-Csv -LiteralPath $reviewPath -NoTypeInformation -Encoding utf8

$parsedSummary = $summaryJson | ConvertFrom-Json
$report = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    mode = "READ_ONLY_DRY_RUN"
    database = $DatabaseName
    source = if ($DatabaseHost) { "$DatabaseHost`:$DatabasePort" } else { "docker:$ContainerName" }
    normalization = "FoodBrandSearchRules-compatible separator folding"
    summary = $parsedSummary
    normalizationGroups = $groupRows.Count
    reviewRequiredGroups = $reviewRows.Count
    topReviewGroups = @($reviewRows | Select-Object -First $SampleLimit)
    artifacts = [ordered]@{
        groups = $groupsPath
        review = $reviewPath
        missingBrandSample = $missingPath
    }
}
$reportPath = Join-Path $runDirectory "summary.json"
[IO.File]::WriteAllText($reportPath, (($report | ConvertTo-Json -Depth 8) + "`n"), [Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json -Depth 8
