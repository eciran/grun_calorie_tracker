param(
    [string]$BundleDirectory = ".\outputs\product-catalog-aug21\tr-augmentation-v1",
    [string]$ApiBaseUrl = "https://api-staging.gruncalorietracker.com",
    [string]$Token = $env:GRUN_ADMIN_JWT,
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$manifestPath = Join-Path $BundleDirectory 'manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($manifest.releaseClassification -ne 'STAGING_PRIVATE_TEST_ONLY' -or $manifest.productionSafe -ne $false) { throw 'This importer accepts only the staging-private augmentation bundle.' }
$importMode = if ([string]::IsNullOrWhiteSpace([string]$manifest.requiredImportMode)) { 'RAW_EXTERNAL' } else { [string]$manifest.requiredImportMode }
$importFormat = if ([string]::IsNullOrWhiteSpace([string]$manifest.requiredImportFormat)) { 'GRUN_STANDARD' } else { [string]$manifest.requiredImportFormat }
if ($importMode -notin @('RAW_EXTERNAL','CURATED_ADMIN')) { throw "Unsupported manifest import mode: $importMode" }
$verifiedRows = 0
foreach ($chunk in $manifest.chunks) {
    $path = Join-Path $BundleDirectory $chunk.file
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing chunk: $($chunk.file)" }
    if ((Get-FileHash $path -Algorithm SHA256).Hash -ne $chunk.sha256) { throw "Hash mismatch: $($chunk.file)" }
    if (@(Import-Csv $path).Count -ne [int]$chunk.rows) { throw "Row mismatch: $($chunk.file)" }
    $verifiedRows += [int]$chunk.rows
}
$preflight = [ordered]@{status='PREFLIGHT_PASS';releaseId=$manifest.releaseId;classification=$manifest.releaseClassification;chunks=$manifest.chunks.Count;rows=$verifiedRows;importMode=$importMode;importFormat=$importFormat;apiBaseUrl=$ApiBaseUrl;execute=[bool]$Execute}
if (-not $Execute) { $preflight | ConvertTo-Json -Depth 5; exit 0 }
if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Execute requires GRUN_ADMIN_JWT or -Token with catalog-manage permission.' }

$results = @()
foreach ($chunk in $manifest.chunks) {
    $path = (Resolve-Path (Join-Path $BundleDirectory $chunk.file)).Path
    $uri = "$($ApiBaseUrl.TrimEnd('/'))/api/v1/admin/products/import?importMode=$importMode&importFormat=$importFormat"
    $client = [Net.Http.HttpClient]::new(); $client.Timeout=[TimeSpan]::FromMinutes(15)
    $client.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)
    $multipart=[Net.Http.MultipartFormDataContent]::new(); $stream=[IO.File]::OpenRead($path); $content=[Net.Http.StreamContent]::new($stream)
    $content.Headers.ContentType=[Net.Http.Headers.MediaTypeHeaderValue]::new('text/csv'); $multipart.Add($content,'file',[IO.Path]::GetFileName($path))
    try { $response=$client.PostAsync($uri,$multipart).GetAwaiter().GetResult(); $json=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult(); if(-not $response.IsSuccessStatusCode){throw "HTTP $([int]$response.StatusCode): $json"}; $results += ($json|ConvertFrom-Json) }
    finally { $content.Dispose();$stream.Dispose();$multipart.Dispose();$client.Dispose() }
}
[ordered]@{status='PASS';releaseId=$manifest.releaseId;results=$results} | ConvertTo-Json -Depth 10
