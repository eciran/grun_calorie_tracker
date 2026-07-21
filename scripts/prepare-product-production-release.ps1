param(
    [string] $StagingBundleDirectory = ".\outputs\product-data-readiness\staging-rehearsal",
    [string] $TrAssessmentPath = ".\outputs\product-data-readiness\s9-tr-internet\tr-off-candidates.tsv",
    [string] $OutputDirectory = ".\outputs\product-data-readiness\production-release-v1"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
function Project-Path([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $projectRoot $Path
}
function File-Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Csv-Rows([string]$Path) { return @(Import-Csv -LiteralPath $Path).Count }

$stagingRoot = Project-Path $StagingBundleDirectory
$assessmentPath = Project-Path $TrAssessmentPath
$outputRoot = Project-Path $OutputDirectory
$sourceManifestPath = Join-Path $stagingRoot "bundle-manifest.json"
if (-not (Test-Path $sourceManifestPath)) { throw "S10 bundle manifest not found." }
if (-not (Test-Path $assessmentPath)) { throw "TR assessment artifact not found." }

$sourceManifest = Get-Content $sourceManifestPath -Raw | ConvertFrom-Json
$strictTrBarcodes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
Import-Csv $assessmentPath -Delimiter ([char]9) |
    Where-Object { $_.tier -eq "STRICT" } |
    ForEach-Object { [void]$strictTrBarcodes.Add($_.barcode) }
if ($strictTrBarcodes.Count -ne 1595) { throw "Expected 1595 latest strict TR barcodes, got $($strictTrBarcodes.Count)." }

if (Test-Path $outputRoot) {
    $resolvedOutput = (Resolve-Path $outputRoot).Path
    $resolvedWorkspace = (Resolve-Path $projectRoot).Path
    $requiredSuffix = [IO.Path]::Combine("outputs", "product-data-readiness", "production-release-v1")
    if (-not $resolvedOutput.StartsWith($resolvedWorkspace) -or -not $resolvedOutput.EndsWith($requiredSuffix)) {
        throw "Output cleanup safety check failed."
    }
    Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
}
New-Item -ItemType Directory -Path (Join-Path $outputRoot "import-chunks") -Force | Out-Null

$releaseChunks = [Collections.Generic.List[object]]::new()
foreach ($artifact in $sourceManifest.artifacts) {
    foreach ($chunk in $artifact.chunks) {
        if ($artifact.market -eq "TR") { continue }
        $source = Join-Path $stagingRoot $chunk.file
        $destination = Join-Path $outputRoot $chunk.file
        Copy-Item -LiteralPath $source -Destination $destination -Force
        $releaseChunks.Add([ordered]@{
            market = $artifact.market
            role = $artifact.role
            sequence = $chunk.sequence
            file = $chunk.file
            rows = Csv-Rows $destination
            sha256 = File-Hash $destination
        })
    }
}

$sourceTrPath = Join-Path $stagingRoot "open-food-facts-tr-branded-v1-gate-import.csv"
$trRows = @(Import-Csv $sourceTrPath | Where-Object { $strictTrBarcodes.Contains($_.barcode) })
if ($trRows.Count -ne 1595) { throw "Filtered TR release row count differs: $($trRows.Count)." }
$trRelativePath = "import-chunks/open-food-facts-tr-branded-v1-release-part-001.csv"
$trDestination = Join-Path $outputRoot $trRelativePath
$trRows | Export-Csv -LiteralPath $trDestination -NoTypeInformation -Encoding utf8
$releaseChunks.Add([ordered]@{
    market = "TR"
    role = "BRANDED_IMPORT"
    sequence = 1
    file = $trRelativePath
    rows = $trRows.Count
    sha256 = File-Hash $trDestination
})

$marketOrder = @{UK_IE=1;EU=2;TR=3;GLOBAL=4}
$orderedChunks = @($releaseChunks | Sort-Object @{Expression={$marketOrder[$_.market]}}, sequence)
$totalRows = ($orderedChunks.rows | Measure-Object -Sum).Sum
$manifest = [ordered]@{
    schemaVersion = 1
    releaseId = "product-catalog-v1-limited-tr"
    releaseDate = "2026-07-21"
    sourceS10BundleManifestSha256 = File-Hash $sourceManifestPath
    counts = [ordered]@{
        inputRows = $totalRows
        expectedCanonicalProducts = 49700
        expectedCrossMarketMerges = 2041
        trStrictRows = 1595
    }
    exclusions = [ordered]@{
        reason = "Removed after the post-S10 TR market-evidence cleanup."
        removedFromS10 = 5
        barcodes = @("2000000024941","0862481003613","11103107","2000000114730","8690626066228")
    }
    chunks = $orderedChunks
}
$manifestPath = Join-Path $outputRoot "release-bundle-manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$manifestHash = File-Hash $manifestPath

[pscustomobject]@{
    status = "PASS"
    releaseId = $manifest.releaseId
    inputRows = $totalRows
    canonicalProducts = $manifest.counts.expectedCanonicalProducts
    trStrictRows = $trRows.Count
    removedPostS10Rows = 5
    chunkCount = $orderedChunks.Count
    releaseBundleManifestSha256 = $manifestHash
} | ConvertTo-Json -Depth 5