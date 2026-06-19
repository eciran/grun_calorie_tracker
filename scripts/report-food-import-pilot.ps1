param(
    [string] $ApiBaseUrl = "http://localhost:8080",
    [string] $Token = $env:GRUN_ADMIN_JWT,
    [string] $MarketRegion = "UK_IE",
    [string[]] $SearchTerms = @("milk", "cheese", "chicken", "bread", "yogurt", "tesco", "dunnes")
)

$ErrorActionPreference = "Stop"

function Import-EnvFile {
    $envFile = Join-Path (Get-Location) ".env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return
    }

    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

function Invoke-DbScalarReport {
    param([string] $Sql)

    docker exec -e PGPASSWORD=$env:POSTGRES_PASSWORD grun-postgres `
        psql -h 127.0.0.1 -U $env:POSTGRES_USER -d $env:POSTGRES_DB `
        -t -A -F "|" -c $Sql
}

function Convert-PipeRow {
    param(
        [string] $Row,
        [string[]] $Columns
    )

    $values = ($Row -split "\|")
    $result = [ordered] @{}
    for ($i = 0; $i -lt $Columns.Count; $i++) {
        $result[$Columns[$i]] = if ($i -lt $values.Count) { $values[$i] } else { "" }
    }
    return [pscustomobject] $result
}

Import-EnvFile

if (-not $env:POSTGRES_USER) { $env:POSTGRES_USER = "postgres" }
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "postgres" }
if (-not $env:POSTGRES_DB) { $env:POSTGRES_DB = "grun_calorie_db" }

$summarySql = @"
select
  count(*) as total,
  count(*) filter (where data_source = 'OPEN_FOOD_FACTS') as open_food_facts,
  count(*) filter (where catalog_type = 'BRANDED_PRODUCT') as branded_products,
  count(*) filter (where verification_status = 'RAW_IMPORTED') as raw_imported,
  count(*) filter (where verification_status = 'VERIFIED') as verified,
  count(*) filter (where calories is null) as missing_calories,
  count(*) filter (where protein is null or fat is null or carbs is null) as missing_macros,
  count(*) filter (where brand is null or trim(brand) = '') as missing_brand,
  count(*) filter (where barcode is null or trim(barcode) = '') as missing_barcode,
  count(*) filter (where image_status = 'NEEDS_REVIEW') as image_needs_review,
  count(*) filter (where image_status = 'APPROVED') as image_approved,
  count(*) filter (where image_status = 'RAW') as image_raw,
  round(avg(coalesce(quality_score, 0))::numeric, 2) as avg_quality_score,
  round(avg(coalesce(confidence_score, 0))::numeric, 2) as avg_confidence_score,
  count(*) filter (where auto_approved_for_catalog = true) as auto_approved_for_catalog,
  count(*) filter (
    where verification_status = 'RAW_IMPORTED'
      and coalesce(auto_approved_for_catalog, false) = false
  ) as review_queue
from food_items
where market_region = '$MarketRegion';
"@

$qualityIssueSql = @"
select issue_type, count(*)
from food_product_quality_issues i
join food_items f on f.id = i.food_item_id
where f.market_region = '$MarketRegion'
  and i.resolved = false
group by issue_type
order by count(*) desc, issue_type;
"@

$duplicatesSql = @"
select coalesce(count(*), 0)
from (
  select normalized_barcode
  from food_items
  where market_region = '$MarketRegion'
    and normalized_barcode is not null
  group by normalized_barcode
  having count(*) > 1
) d;
"@

$summary = Invoke-DbScalarReport -Sql $summarySql
$qualityIssues = Invoke-DbScalarReport -Sql $qualityIssueSql
$duplicates = Invoke-DbScalarReport -Sql $duplicatesSql
$summaryObject = Convert-PipeRow `
    -Row $summary `
    -Columns @(
        "total",
        "openFoodFacts",
        "brandedProducts",
        "rawImported",
        "verified",
        "missingCalories",
        "missingMacros",
        "missingBrand",
        "missingBarcode",
        "imageNeedsReview",
        "imageApproved",
        "imageRaw",
        "avgQualityScore",
        "avgConfidenceScore",
        "autoApprovedForCatalog",
        "reviewQueue"
    )
$qualityIssueObjects = @()
if ($qualityIssues) {
    $qualityIssueObjects = $qualityIssues |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { Convert-PipeRow -Row $_ -Columns @("issueType", "count") }
}

$searchResults = @()
if (-not [string]::IsNullOrWhiteSpace($Token)) {
    foreach ($term in $SearchTerms) {
        $encodedTerm = [System.Uri]::EscapeDataString($term)
        try {
            $result = Invoke-RestMethod `
                -Uri "$ApiBaseUrl/api/v1/products/search?q=$encodedTerm&region=$MarketRegion&page=0&size=5" `
                -Method Get `
                -Headers @{ Authorization = "Bearer $Token" }
            $searchResults += [pscustomobject] @{
                term = $term
                totalElements = $result.totalElements
                firstResult = if ($result.content.Count -gt 0) { $result.content[0].productName } else { "" }
            }
        } catch {
            $searchResults += [pscustomobject] @{
                term = $term
                totalElements = "ERROR"
                firstResult = $_.Exception.Message
            }
        }
    }
}

[pscustomobject] @{
    marketRegion = $MarketRegion
    summary = $summaryObject
    activeQualityIssues = $qualityIssueObjects
    duplicateBarcodeGroups = $duplicates
    searchResults = $searchResults
} | ConvertTo-Json -Depth 6
