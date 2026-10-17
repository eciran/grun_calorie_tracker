param(
    [string]$InputPath = ".\outputs\serving-option-candidates\liquid-review\live-off-analysis\ml_physical_form_source_supported.csv",
    [string]$OutputDirectory = ".\outputs\serving-option-candidates\liquid-review\nutrition-evidence",
    [ValidateRange(4500, 60000)]
    [int]$DelayMilliseconds = 4500
)

$ErrorActionPreference = "Stop"
$workspace = [IO.Path]::GetFullPath((Get-Location).Path)
$input = [IO.Path]::GetFullPath((Join-Path $workspace $InputPath))
$output = [IO.Path]::GetFullPath((Join-Path $workspace $OutputDirectory))
if (-not $input.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase) -or
    -not $output.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Input and output paths must stay inside the workspace."
}
if (-not (Test-Path -LiteralPath $input)) { throw "Input not found: $input" }

$itemsPath = Join-Path $output "items"
New-Item -ItemType Directory -Force -Path $itemsPath | Out-Null
$rows = @(Import-Csv -LiteralPath $input)
$results = [Collections.Generic.List[object]]::new()

foreach ($row in $rows) {
    $barcode = [string]$row.barcode
    $itemPath = Join-Path $itemsPath "barcode-$barcode.json"
    if (-not $barcode -or $barcode -notmatch '^\d{8,14}$') {
        $results.Add([pscustomobject]@{ productId=$row.product_id; barcode=$barcode; status='INVALID_BARCODE' })
        continue
    }
    if (Test-Path -LiteralPath $itemPath) {
        $cached = Get-Content -LiteralPath $itemPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($cached.status -ne 'FETCH_FAILED') { $results.Add($cached); continue }
    }

    $fields = 'code,product_name,brands,nutrition_data_per,nutrition_data_prepared_per,serving_size,serving_quantity,serving_quantity_unit,product_quantity,product_quantity_unit,quantity,nutriments,last_modified_t'
    $url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=$fields"
    try {
        $response = Invoke-RestMethod -Uri $url -TimeoutSec 25 -Headers @{
            'User-Agent' = 'GRunCatalogReview/1.0 (read-only liquid nutrition reconciliation)'
        }
        $result = [pscustomobject][ordered]@{
            productId = $row.product_id
            barcode = $barcode
            checkedAt = [DateTime]::UtcNow.ToString('o')
            status = if ($response.status -eq 1) { 'FETCHED' } else { 'NOT_FOUND' }
            product = $response.product
        }
    } catch {
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $result = [pscustomobject][ordered]@{
            productId=$row.product_id; barcode=$barcode; checkedAt=[DateTime]::UtcNow.ToString('o')
            status=if ($statusCode -eq 404) { 'NOT_FOUND' } else { 'FETCH_FAILED' }
            httpStatus=$statusCode; product=$null
        }
    }
    [IO.File]::WriteAllText($itemPath, ($result | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    $results.Add($result)
    if ($result.status -eq 'FETCH_FAILED') {
        break
    }
    Start-Sleep -Milliseconds $DelayMilliseconds
}

$summary = [ordered]@{
    generatedAt=[DateTime]::UtcNow.ToString('o'); queued=$rows.Count; processed=$results.Count
    fetched=@($results | Where-Object status -eq 'FETCHED').Count
    notFound=@($results | Where-Object status -eq 'NOT_FOUND').Count
    failed=@($results | Where-Object status -eq 'FETCH_FAILED').Count
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'summary.json') -Encoding utf8
$summary | ConvertTo-Json
