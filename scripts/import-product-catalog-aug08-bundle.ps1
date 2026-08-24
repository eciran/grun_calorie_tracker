param(
    [string]$BundleDirectory = ".\outputs\product-catalog-aug08\licensed-default-20260802",
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$Token = $env:GRUN_ADMIN_JWT,
    [switch]$Execute,
    [switch]$ExpectedEmptyDatabase
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$projectRoot = Split-Path -Parent $PSScriptRoot
$bundleRoot = if ([IO.Path]::IsPathRooted($BundleDirectory)) {
    (Resolve-Path -LiteralPath $BundleDirectory).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $projectRoot $BundleDirectory)).Path
}
$manifestPath = Join-Path $bundleRoot "bundle-manifest.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$chunks = @($manifest.artifacts | ForEach-Object { $_.chunks })

if ($manifest.requiredImportMode -ne "RAW_EXTERNAL" -or $manifest.requiredImportFormat -ne "GRUN_STANDARD") {
    throw "Unsupported import contract in bundle manifest."
}
if ($manifest.releaseId -notlike "product-catalog-aug08-licensed-default-*") {
    throw "Only the licensed-default August 8 bundle is accepted by this command."
}
if ($chunks.Count -eq 0) { throw "Bundle manifest has no import chunks." }

$verifiedRows = 0
foreach ($chunk in $chunks) {
    $chunkPath = Join-Path $bundleRoot $chunk.file
    if (-not (Test-Path -LiteralPath $chunkPath -PathType Leaf)) {
        throw "Missing chunk: $($chunk.file)"
    }
    $actualHash = (Get-FileHash -LiteralPath $chunkPath -Algorithm SHA256).Hash
    if ($actualHash -ne $chunk.sha256) { throw "Chunk hash differs: $($chunk.file)" }
    if ((Get-Item -LiteralPath $chunkPath).Length -gt 5767168) {
        throw "Chunk exceeds the safe multipart size: $($chunk.file)"
    }
    $verifiedRows += [int]$chunk.rows
}
if ($verifiedRows -ne [int]$manifest.counts.inputRows) {
    throw "Manifest and chunk row totals differ."
}

$preflight = [ordered]@{
    status = "PREFLIGHT_PASS"
    releaseId = $manifest.releaseId
    manifestPath = $manifestPath
    manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
    chunks = $chunks.Count
    inputRows = $verifiedRows
    expectedCanonicalProducts = [int]$manifest.counts.expectedCanonicalProducts
    expectedCrossMarketMerges = [int]$manifest.counts.expectedCrossMarketMerges
    markets = $manifest.counts.markets
}

if (-not $Execute) {
    [pscustomobject]$preflight | ConvertTo-Json -Depth 6
    Write-Output "PREFLIGHT_ONLY: rerun with -Execute and a catalog-manage admin JWT to import."
    exit 0
}
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "-Execute requires -Token or GRUN_ADMIN_JWT."
}

$results = @()
foreach ($chunk in $chunks) {
    $chunkPath = Join-Path $bundleRoot $chunk.file
    $uri = "$($ApiBaseUrl.TrimEnd('/'))/api/v1/admin/products/import?importMode=RAW_EXTERNAL&importFormat=GRUN_STANDARD"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $client = [Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromMinutes(15)
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    $multipart = [Net.Http.MultipartFormDataContent]::new()
    $fileStream = [IO.File]::OpenRead($chunkPath)
    $fileContent = [Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new("text/csv")
    $multipart.Add($fileContent, "file", [IO.Path]::GetFileName($chunkPath))
    try {
        $response = $client.PostAsync($uri, $multipart).GetAwaiter().GetResult()
        $json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Import failed for $($chunk.file): HTTP $([int]$response.StatusCode). Response: $json"
        }
    } finally {
        $fileContent.Dispose()
        $fileStream.Dispose()
        $multipart.Dispose()
        $client.Dispose()
        $watch.Stop()
    }
    $result = $json | ConvertFrom-Json
    if ([int]$result.totalRows -ne [int]$chunk.rows -or
        [int]$result.savedRows -ne [int]$chunk.rows -or
        [int]$result.skippedRows -ne 0 -or
        [int]$result.duplicateInputRows -ne 0) {
        throw "Import gate failed for $($chunk.file)."
    }
    $results += [pscustomobject]@{
        file = $chunk.file
        rows = [int]$chunk.rows
        inserted = [int]$result.insertedRows
        updated = [int]$result.updatedRows
        saved = [int]$result.savedRows
        skipped = [int]$result.skippedRows
        elapsedMs = $watch.ElapsedMilliseconds
    }
}

$totals = [ordered]@{
    rows = [int](($results.rows | Measure-Object -Sum).Sum)
    inserted = [int](($results.inserted | Measure-Object -Sum).Sum)
    updated = [int](($results.updated | Measure-Object -Sum).Sum)
    saved = [int](($results.saved | Measure-Object -Sum).Sum)
    skipped = [int](($results.skipped | Measure-Object -Sum).Sum)
}
if ($ExpectedEmptyDatabase -and (
    $totals.inserted -ne [int]$manifest.counts.expectedCanonicalProducts -or
    $totals.updated -ne [int]$manifest.counts.expectedCrossMarketMerges)) {
    throw "Empty-database totals differ from the rehearsed manifest contract."
}

$runRoot = Join-Path $projectRoot "outputs\product-catalog-aug08\import-runs"
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
$stamp = [DateTimeOffset]::Now.ToString("yyyyMMdd-HHmmss")
$reportPath = Join-Path $runRoot "licensed-default-$stamp.json"
$report = [ordered]@{
    status = "PASS"
    importedAt = [DateTimeOffset]::Now.ToString("O")
    preflight = $preflight
    apiBaseUrl = $ApiBaseUrl
    expectedEmptyDatabase = [bool]$ExpectedEmptyDatabase
    totals = $totals
    chunks = $results
}
[IO.File]::WriteAllText(
    $reportPath,
    (($report | ConvertTo-Json -Depth 12) + "`n"),
    [Text.UTF8Encoding]::new($false)
)
$report | ConvertTo-Json -Depth 12
Write-Output "IMPORT_PASS: $reportPath"
