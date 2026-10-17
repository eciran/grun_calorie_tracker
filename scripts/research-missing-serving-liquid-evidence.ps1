param(
    [string]$InputPath = ".\outputs\serving-option-candidates\liquid-review\potential_liquid_requires_source_fetch.csv",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review\live-off-evidence",
    [ValidateRange(4500, 60000)]
    [int]$DelayMilliseconds = 4500
)

$ErrorActionPreference = "Stop"
$input = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $InputPath))
$output = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputDirectory))
$workspace = [System.IO.Path]::GetFullPath((Get-Location).Path)
if (-not $input.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase) -or
    -not $output.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Input and output paths must stay inside the workspace."
}
if (-not (Test-Path $input)) {
    throw "Candidate input not found: $input"
}

New-Item -ItemType Directory -Force -Path $output | Out-Null
$itemsPath = Join-Path $output "items"
New-Item -ItemType Directory -Force -Path $itemsPath | Out-Null
$rows = @(Import-Csv -LiteralPath $input)
$results = [Collections.Generic.List[object]]::new()

foreach ($row in $rows) {
    $barcode = [string]$row.barcode
    $itemPath = Join-Path $itemsPath "barcode-$barcode.json"
    if (-not $barcode -or $barcode -notmatch '^\d{8,14}$') {
        $results.Add([pscustomobject]@{ productId=$row.product_id; barcode=$barcode; status="INVALID_OR_MISSING_BARCODE"; product=$null })
        continue
    }
    if (Test-Path $itemPath) {
        $cached = Get-Content $itemPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($cached.status -notin @("FETCH_FAILED")) {
            $results.Add($cached)
            continue
        }
    }

    $url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=code,product_name,brands,categories_tags,nutrition_data_per,serving_size,serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit,quantity,last_modified_t"
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 20 -Headers @{
            "User-Agent" = "GRunCatalogReview/1.0 (read-only serving unit evidence)"
        }
        $result = [pscustomobject][ordered]@{
            productId = $row.product_id
            barcode = $barcode
            checkedAt = [DateTime]::UtcNow.ToString("o")
            status = if ($response.status -eq 1) { "FETCHED" } else { "NOT_FOUND" }
            product = $response.product
        }
    } catch {
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $result = [pscustomobject][ordered]@{
            productId = $row.product_id
            barcode = $barcode
            checkedAt = [DateTime]::UtcNow.ToString("o")
            status = if ($statusCode -eq 404) { "NOT_FOUND" } else { "FETCH_FAILED" }
            httpStatus = $statusCode
            product = $null
        }
    }
    [IO.File]::WriteAllText($itemPath, ($result | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    $results.Add($result)
    if ($result.status -eq "FETCH_FAILED") {
        break
    }
    Start-Sleep -Milliseconds $DelayMilliseconds
}

$summary = [ordered]@{
    generatedAt = [DateTime]::UtcNow.ToString("o")
    queued = $rows.Count
    processed = $results.Count
    fetched = @($results | Where-Object status -eq "FETCHED").Count
    notFound = @($results | Where-Object status -eq "NOT_FOUND").Count
    invalidBarcode = @($results | Where-Object status -eq "INVALID_OR_MISSING_BARCODE").Count
    failed = @($results | Where-Object status -eq "FETCH_FAILED").Count
    per100ml = @($results | Where-Object { $_.status -eq "FETCHED" -and $_.product.nutrition_data_per -eq "100ml" }).Count
    per100g = @($results | Where-Object { $_.status -eq "FETCHED" -and $_.product.nutrition_data_per -eq "100g" }).Count
    perServing = @($results | Where-Object { $_.status -eq "FETCHED" -and $_.product.nutrition_data_per -eq "serving" }).Count
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output "summary.json") -Encoding utf8
$summary | ConvertTo-Json -Depth 5
