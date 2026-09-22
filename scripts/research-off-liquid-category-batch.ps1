param(
    [ValidateRange(1, 100)]
    [int] $Limit = 60,
    [ValidateRange(500, 10000)]
    [int] $DelayMilliseconds = 1200
)

$ErrorActionPreference = "Stop"
$auditRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "outputs/product-duplicate-audit-2026-09-14"
$triage = Get-Content (Join-Path $auditRoot "LIQUID_CATEGORY_TRIAGE.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$progress = Get-Content (Join-Path $auditRoot "LIQUID_UNIT_PROGRESS.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$knownById = @{}
foreach ($row in $progress.rows) {
    $knownById[[string] $row.id] = $row.status
}
$completedBarcodes = [Collections.Generic.HashSet[string]]::new()
$evidenceRoot = Join-Path $auditRoot "liquid-category-evidence"
if (Test-Path $evidenceRoot) {
    foreach ($summaryFile in (Get-ChildItem $evidenceRoot -Recurse -Filter summary.json -File)) {
        $previous = Get-Content $summaryFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($result in @($previous.results | Where-Object status -eq "FETCHED")) {
            [void] $completedBarcodes.Add([string] $result.barcode)
        }
    }
}

$targetStatuses = @("UNIT_SOURCE_EVIDENCE_REQUIRED")
$queue = @($triage.matches | Where-Object {
    $_.decision -eq "LIQUID_CATEGORY_SOURCE_BASIS_REQUIRED" -and
    $targetStatuses -contains $knownById[[string] $_.id] -and
    $_.barcode -match "^\d{8,14}$" -and
    -not $completedBarcodes.Contains([string] $_.barcode)
} | Sort-Object id)

$stamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
$outputRoot = Join-Path $auditRoot "liquid-category-evidence/$stamp"
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$results = [Collections.Generic.List[object]]::new()

foreach ($row in ($queue | Select-Object -First $Limit)) {
    $url = "https://world.openfoodfacts.org/api/v2/product/$($row.barcode).json?fields=code,product_name,categories_tags,nutrition_data_per,serving_size,serving_quantity,serving_quantity_unit,quantity"
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 15 -Headers @{
            "User-Agent" = "GRunCatalogReview/1.0 (read-only liquid unit evidence)"
        }
        $result = [ordered]@{
            id = $row.id
            barcode = $row.barcode
            checkedAt = [DateTime]::UtcNow.ToString("o")
            url = $url
            status = if ($response.status -eq 1) { "FETCHED" } else { "NOT_FOUND" }
            product = $response.product
            applyAllowed = $false
        }
    } catch {
        $statusCode = if ($_.Exception.Response) { [int] $_.Exception.Response.StatusCode } else { 0 }
        $result = [ordered]@{
            id = $row.id
            barcode = $row.barcode
            checkedAt = [DateTime]::UtcNow.ToString("o")
            url = $url
            status = "FETCH_FAILED"
            httpStatus = $statusCode
            failureType = $_.Exception.GetType().Name
            applyAllowed = $false
        }
    }
    $results.Add([pscustomobject] $result)
    if ($result.status -eq "FETCH_FAILED" -and $result.httpStatus -in @(403, 429)) {
        break
    }
    Start-Sleep -Milliseconds $DelayMilliseconds
}

$summary = [pscustomobject][ordered]@{
    scope = "LIQUID_CATEGORY_SOURCE_BASIS_EVIDENCE"
    generatedAt = [DateTime]::UtcNow.ToString("o")
    queued = $queue.Count
    attempted = $results.Count
    fetched = @($results | Where-Object status -eq "FETCHED").Count
    failed = @($results | Where-Object status -eq "FETCH_FAILED").Count
    notFound = @($results | Where-Object status -eq "NOT_FOUND").Count
    per100ml = @($results | Where-Object { $_.status -eq "FETCHED" -and $_.product.nutrition_data_per -eq "100ml" }).Count
    per100g = @($results | Where-Object { $_.status -eq "FETCHED" -and $_.product.nutrition_data_per -eq "100g" }).Count
    basisMissing = @($results | Where-Object { $_.status -eq "FETCHED" -and [string]::IsNullOrWhiteSpace($_.product.nutrition_data_per) }).Count
    applyAllowed = $false
    results = $results
}

$summaryPath = Join-Path $outputRoot "summary.json"
[IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
$summary | Select-Object scope,generatedAt,queued,attempted,fetched,failed,notFound,per100ml,per100g,basisMissing,applyAllowed | ConvertTo-Json
