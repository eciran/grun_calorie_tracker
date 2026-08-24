param(
    [string]$PackageDirectory = ".\outputs\local-food-catalog-aug08\release\test-build-db-20260809",
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$Token = $env:GRUN_ADMIN_JWT,
    [switch]$Execute,
    [switch]$IncludePrivateTrOverlay
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$package = Resolve-Path (Join-Path $root $PackageDirectory)
$manifestPath = Join-Path $package "test-build-product-manifest.json"
$manifest = Get-Content $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.releaseId -ne "grun-test-build-products-20260809") { throw "Unexpected test-build release ID." }
foreach ($ref in $manifest.references) {
    $path = Join-Path $root ($ref.path -replace '^\.\\','')
    if (-not (Test-Path $path -PathType Leaf)) { throw "Missing reference: $($ref.id)" }
    if ((Get-FileHash $path -Algorithm SHA256).Hash -ne $ref.sha256) { throw "Hash mismatch: $($ref.id)" }
}

$result = [ordered]@{
    status="PREFLIGHT_PASS"; releaseId=$manifest.releaseId
    licensedRows=$manifest.importReady.licensedDefault.rows
    includePrivateTrOverlay=[bool]$IncludePrivateTrOverlay
    privateOverlayRows=if($IncludePrivateTrOverlay){$manifest.importReady.trPrivateTestOverlayV10.rows}else{0}
    expectedCanonicalProducts=if($IncludePrivateTrOverlay){$manifest.expectedAfterExplicitOverlay.canonicalProducts}else{$manifest.importReady.licensedDefault.expectedCanonicalProducts}
    localDishImportRows=0; restaurantChainImportRows=0
}
if (-not $Execute) { $result | ConvertTo-Json -Depth 5; exit 0 }
if ([string]::IsNullOrWhiteSpace($Token)) { throw "-Execute requires -Token or GRUN_ADMIN_JWT." }

& (Join-Path $PSScriptRoot "import-product-catalog-aug08-bundle.ps1") -ApiBaseUrl $ApiBaseUrl -Token $Token -Execute -ExpectedEmptyDatabase
if ($LASTEXITCODE -ne 0) { throw "Licensed-default import failed." }
if ($IncludePrivateTrOverlay) {
    $overlayRef = $manifest.references | Where-Object id -eq "tr_private_overlay_v10"
    $overlayPath = Join-Path $root ($overlayRef.path -replace '^\.\\','')
    $uri = "$($ApiBaseUrl.TrimEnd('/'))/api/v1/admin/products/import?importMode=RAW_EXTERNAL&importFormat=GRUN_STANDARD"
    Add-Type -AssemblyName System.Net.Http
    $client = [Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromMinutes(15)
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    $multipart = [Net.Http.MultipartFormDataContent]::new()
    $fileStream = [IO.File]::OpenRead($overlayPath)
    $fileContent = [Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new("text/csv")
    $multipart.Add($fileContent, "file", [IO.Path]::GetFileName($overlayPath))
    try {
        $httpResponse = $client.PostAsync($uri, $multipart).GetAwaiter().GetResult()
        $body = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $httpResponse.IsSuccessStatusCode) { throw "Private overlay import failed: HTTP $([int]$httpResponse.StatusCode). $body" }
        $response = $body | ConvertFrom-Json
    } finally {
        $fileContent.Dispose(); $fileStream.Dispose(); $multipart.Dispose(); $client.Dispose()
    }
    if ([int]$response.savedRows -ne [int]$manifest.importReady.trPrivateTestOverlayV10.rows -or [int]$response.skippedRows -ne 0) {
        throw "Private overlay import totals failed."
    }
}
