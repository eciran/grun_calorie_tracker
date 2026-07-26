param(
    [string] $ReleaseManifest = ".\sample-data\manifests\product-catalog-release-v1.json",
    [string] $BundleDirectory = ".\outputs\product-data-readiness\production-release-v1"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

function Resolve-ProjectPath([string] $Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $projectRoot $Path
}

function Assert-Equal($Expected, $Actual, [string] $Message) {
    if ($Expected -ne $Actual) {
        throw "$Message Expected=$Expected Actual=$Actual"
    }
}

$releasePath = Resolve-ProjectPath $ReleaseManifest
$bundleRoot = Resolve-ProjectPath $BundleDirectory
$bundleManifestPath = Join-Path $bundleRoot "release-bundle-manifest.json"

if (-not (Test-Path -LiteralPath $releasePath -PathType Leaf)) { throw "Release manifest not found: $releasePath" }
if (-not (Test-Path -LiteralPath $bundleManifestPath -PathType Leaf)) { throw "Bundle manifest not found: $bundleManifestPath" }

$release = Get-Content -LiteralPath $releasePath -Raw | ConvertFrom-Json
$bundle = Get-Content -LiteralPath $bundleManifestPath -Raw | ConvertFrom-Json
$bundleHash = (Get-FileHash -LiteralPath $bundleManifestPath -Algorithm SHA256).Hash

Assert-Equal $release.releaseBundleManifestSha256 $bundleHash "Release bundle manifest checksum differs."
Assert-Equal $release.sourceBundleManifestSha256 $bundle.sourceS10BundleManifestSha256 "S10 source bundle checksum differs."
Assert-Equal $release.expectedInputRows $bundle.counts.inputRows "Input row count differs."
Assert-Equal $release.expectedCanonicalProducts $bundle.counts.expectedCanonicalProducts "Canonical count differs."
Assert-Equal $release.expectedCrossMarketMerges $bundle.counts.expectedCrossMarketMerges "Cross-market merge count differs."
Assert-Equal $release.markets.TR $bundle.counts.trStrictRows "TR strict row count differs."
Assert-Equal $false $release.acceptance.s9VolumeGatePassed "Limited TR release must not claim the S9 volume gate passed."
Assert-Equal $true $release.acceptance.productionCandidate "Release is not approved as a production candidate."

$verifiedFiles = 0
foreach ($chunk in $bundle.chunks) {
    $chunkPath = Join-Path $bundleRoot $chunk.file
    if (-not (Test-Path -LiteralPath $chunkPath -PathType Leaf)) { throw "Import chunk not found: $($chunk.file)" }
    Assert-Equal $chunk.sha256 (Get-FileHash -LiteralPath $chunkPath -Algorithm SHA256).Hash "Chunk checksum differs: $($chunk.file)."
    Assert-Equal $chunk.rows @(Import-Csv -LiteralPath $chunkPath).Count "Chunk row count differs: $($chunk.file)."
    $verifiedFiles++
}
[PSCustomObject]@{
    status = "PASS"
    releaseId = $release.releaseId
    acceptanceMode = $release.acceptance.mode
    s9VolumeGatePassed = $release.acceptance.s9VolumeGatePassed
    inputRows = $bundle.counts.inputRows
    canonicalProducts = $bundle.counts.expectedCanonicalProducts
    verifiedFiles = $verifiedFiles
    bundleManifestSha256 = $bundleHash
} | ConvertTo-Json -Depth 5
