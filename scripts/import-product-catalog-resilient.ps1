param(
    [Parameter(Mandatory = $true)][string]$BundleDirectory,
    [string]$ApiBaseUrl = "https://api-staging.gruncalorietracker.com",
    [string]$Token = $env:GRUN_ADMIN_JWT,
    [int]$StartChunk = 1,
    [ValidateRange(0, 10000)][int]$StartRow = 0,
    [ValidateRange(1, 10000)][int]$UploadBatchRows = 2000,
    [string]$OutputDirectory = ".\outputs\product-catalog-aug08\resilient-import-runs"
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Token)) { throw "Token or GRUN_ADMIN_JWT is required." }

$root = Split-Path -Parent $PSScriptRoot
$bundle = if ([IO.Path]::IsPathRooted($BundleDirectory)) {
    (Resolve-Path -LiteralPath $BundleDirectory).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $root $BundleDirectory)).Path
}
$manifest = Get-Content (Join-Path $bundle "bundle-manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$chunks = @($manifest.artifacts | ForEach-Object { $_.chunks })
$headers = @{ Authorization = "Bearer $Token" }
$results = @()

function Test-BatchCommitted {
    param([string]$BatchPath)
    $sample = Import-Csv -LiteralPath $BatchPath |
        Where-Object { $_.barcode -and $_.source_categories } |
        Select-Object -Last 1
    if ($null -eq $sample) { return $false }
    try {
        $product = Invoke-RestMethod -Uri "$($ApiBaseUrl.TrimEnd('/'))/api/v1/products/barcode/$($sample.barcode)" `
            -Headers $headers -Method Get -TimeoutSec 30
        $expected = @($sample.source_categories -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $actual = @($product.sourceCategoryTags)
        return $expected.Count -gt 0 -and @($expected | Where-Object { $_ -notin $actual }).Count -eq 0
    } catch {
        return $false
    }
}

for ($index = $StartChunk - 1; $index -lt $chunks.Count; $index++) {
    $chunk = $chunks[$index]
    $path = Join-Path $bundle $chunk.file
    $actualHash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ($actualHash -ne $chunk.sha256) { throw "Hash mismatch: $($chunk.file)" }

    $uri = "$($ApiBaseUrl.TrimEnd('/'))/api/v1/admin/products/import?importMode=RAW_EXTERNAL&importFormat=GRUN_STANDARD"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $statuses = @()
    $chunkRows = @(Import-Csv -LiteralPath $path)
    if ($index -eq ($StartChunk - 1) -and $StartRow -gt 0) {
        $chunkRows = @($chunkRows | Select-Object -Skip $StartRow)
    }
    $batchCount = [Math]::Ceiling($chunkRows.Count / [double]$UploadBatchRows)
    $tempDirectory = Join-Path ([IO.Path]::GetTempPath()) ("grun-import-{0}-{1}" -f $PID, [guid]::NewGuid())
    New-Item -ItemType Directory -Path $tempDirectory -Force | Out-Null
    try {
        for ($batchIndex = 0; $batchIndex -lt $batchCount; $batchIndex++) {
            $offset = $batchIndex * $UploadBatchRows
            $batchRows = @($chunkRows | Select-Object -Skip $offset -First $UploadBatchRows)
            $batchPath = Join-Path $tempDirectory ("batch-{0:D3}.csv" -f ($batchIndex + 1))
            $batchRows | Export-Csv -LiteralPath $batchPath -NoTypeInformation -Encoding utf8
            $status = ""
            try {
                $response = Invoke-RestMethod -Uri $uri -Headers $headers -Method Post -Form @{ file = Get-Item $batchPath } -TimeoutSec 180
                if ([int]$response.totalRows -ne $batchRows.Count -or [int]$response.savedRows -ne $batchRows.Count -or
                    [int]$response.skippedRows -ne 0 -or [int]$response.duplicateInputRows -ne 0) {
                    throw "Import response gate failed for $($chunk.file), batch $($batchIndex + 1)/$batchCount."
                }
                $status = "HTTP_SUCCESS"
            } catch {
                $httpStatus = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
                if ($httpStatus -ne 504) { throw }
                $committed = $false
                for ($attempt = 1; $attempt -le 24 -and -not $committed; $attempt++) {
                    Start-Sleep -Seconds 5
                    $committed = Test-BatchCommitted -BatchPath $batchPath
                }
                if (-not $committed) {
                    throw "Gateway timeout was not followed by category evidence for $($chunk.file), batch $($batchIndex + 1)/$batchCount."
                }
                $status = "TIMEOUT_COMMIT_VERIFIED"
            }
            $statuses += $status
            Write-Output ("BATCH_PASS {0}/{1} {2}/{3} {4}" -f ($index + 1), $chunks.Count, ($batchIndex + 1), $batchCount, $status)
        }
    } finally {
        $watch.Stop()
        if (Test-Path -LiteralPath $tempDirectory) {
            Remove-Item -LiteralPath $tempDirectory -Recurse -Force
        }
    }
    $status = if ($statuses -contains "TIMEOUT_COMMIT_VERIFIED") { "MIXED_VERIFIED" } else { "HTTP_SUCCESS" }
    $result = [pscustomobject]@{
        sequence = $index + 1
        file = $chunk.file
        rows = [int]$chunk.rows
        status = $status
        elapsedMs = $watch.ElapsedMilliseconds
    }
    $results += $result
    Write-Output ("CHUNK_PASS {0}/{1} {2} {3}" -f ($index + 1), $chunks.Count, $status, $chunk.file)
}

$reportRoot = Join-Path $root $OutputDirectory
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$reportPath = Join-Path $reportRoot ("import-{0}.json" -f [DateTimeOffset]::Now.ToString("yyyyMMdd-HHmmss"))
$report = [ordered]@{
    status = "PASS"
    releaseId = $manifest.releaseId
    completedAt = [DateTimeOffset]::Now.ToString("O")
    startChunk = $StartChunk
    startRow = $StartRow
    chunks = $results
    rows = [int](($results.rows | Measure-Object -Sum).Sum)
}
[IO.File]::WriteAllText($reportPath, (($report | ConvertTo-Json -Depth 8) + "`n"), [Text.UTF8Encoding]::new($false))
Write-Output "IMPORT_PASS $reportPath"
