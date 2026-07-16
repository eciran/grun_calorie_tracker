param(
    [string] $ManifestPath = ".\sample-data\manifests\open-food-facts-uk-ie-v1.json",

    [string] $SourcePath = ".\outputs\openfoodfacts-products.csv.gz",

    [ValidateSet("pilot", "gate")]
    [string] $Stage = "pilot",

    [string] $OutputDirectory = ".\outputs\product-data-readiness",

    [switch] $SkipSourceHashValidation,

    [switch] $ReuseRawArtifact,

    [switch] $AllowIncompleteStage
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $manifestFile = (Resolve-Path -LiteralPath $ManifestPath).Path
    $sourceFile = (Resolve-Path -LiteralPath $SourcePath).Path
    $manifest = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $stageConfig = $manifest.stages.$Stage
    if ($null -eq $stageConfig) { throw "Stage '$Stage' is missing from manifest." }
    if ($manifest.marketRegion -notin @("UK_IE", "TR", "EU")) {
        throw "Unsupported OFF market manifest: $($manifest.marketRegion)"
    }

    $sourceHashTimer = [Diagnostics.Stopwatch]::StartNew()
    $actualSourceHash = if ($SkipSourceHashValidation) { "SKIPPED" } else { (Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash }
    $sourceHashTimer.Stop()
    if (-not $SkipSourceHashValidation -and $actualSourceHash -ne $manifest.source.sha256) {
        throw "OFF source snapshot checksum mismatch. Expected $($manifest.source.sha256), found $actualSourceHash."
    }

    if (-not (Test-Path -LiteralPath $OutputDirectory)) {
        New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    }
    $resolvedOutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
    $prefix = "$($manifest.manifestId)-$Stage"
    $rawPath = Join-Path $resolvedOutputDirectory "$prefix-raw.tsv"
    $importPath = Join-Path $resolvedOutputDirectory "$prefix-import.csv"
    $validationReportPath = Join-Path $resolvedOutputDirectory "$prefix-validation.json"
    $pipelineReportPath = Join-Path $resolvedOutputDirectory "$prefix-report.json"
    $targetRows = [int] $stageConfig.targetRows
    $maxRowsToRead = [int] $stageConfig.maxRowsToRead

    $exportTimer = [Diagnostics.Stopwatch]::StartNew()
    if ($ReuseRawArtifact) {
        if (-not (Test-Path -LiteralPath $rawPath -PathType Leaf)) { throw "Reusable raw artifact not found: $rawPath" }
        $rawRows = ((Get-Content -LiteralPath $rawPath -Encoding UTF8 | Measure-Object -Line).Lines - 1)
        $exportReport = [ordered]@{ reused = $true; rowsWritten = $rawRows; outputPath = $rawPath }
    } else {
        $exportOutput = & .\scripts\export-open-food-facts-bulk-products.ps1 `
            -InputPath $sourceFile `
            -OutputPath $rawPath `
            -MarketRegion $manifest.marketRegion `
            -CountryTerms @($manifest.filters.countryTerms) `
            -Limit $targetRows `
            -MaxRowsToRead $maxRowsToRead `
            -RequireCalories `
            -RequireCompleteMacroData `
            -RequireBrand `
            -RejectImplausibleNutrition
        $exportReport = ($exportOutput -join "`n") | ConvertFrom-Json
    }
    $exportTimer.Stop()

    $convertTimer = [Diagnostics.Stopwatch]::StartNew()
    $convertOutput = & .\scripts\convert-open-food-facts-export.ps1 `
        -InputPath $rawPath `
        -OutputPath $importPath `
        -Limit $targetRows `
        -MarketRegion $manifest.marketRegion `
        -CountryTerms @($manifest.filters.countryTerms) `
        -PriorityStoreTerms @($manifest.filters.priorityStoreTerms) `
        -PriorityStoreTargetPercent ([int]$manifest.filters.priorityStoreTargetPercent) `
        -RequireCalories `
        -RequireMacroData `
        -MaxRowsToRead $targetRows
    $convertReport = ($convertOutput -join "`n") | ConvertFrom-Json
    $convertTimer.Stop()

    $validationTimer = [Diagnostics.Stopwatch]::StartNew()
    $validationOutput = & .\scripts\validate-food-import-batch.ps1 `
        -InputPath $importPath `
        -ManifestPath $manifestFile `
        -Stage $Stage `
        -ReportPath $validationReportPath `
        -FailOnGate:(-not $AllowIncompleteStage)
    $validationReport = ($validationOutput -join "`n") | ConvertFrom-Json
    $validationTimer.Stop()

    $pipelineReport = [ordered]@{
        schemaVersion = 1
        manifestId = $manifest.manifestId
        stage = $Stage
        generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
        sourceSnapshot = [ordered]@{ path = $sourceFile; expectedSha256 = $manifest.source.sha256; actualSha256 = $actualSourceHash }
        durationMs = [ordered]@{
            sourceHash = $sourceHashTimer.ElapsedMilliseconds
            export = $exportTimer.ElapsedMilliseconds
            conversion = $convertTimer.ElapsedMilliseconds
            validation = $validationTimer.ElapsedMilliseconds
            total = $sourceHashTimer.ElapsedMilliseconds + $exportTimer.ElapsedMilliseconds + $convertTimer.ElapsedMilliseconds + $validationTimer.ElapsedMilliseconds
        }
        export = $exportReport
        conversion = $convertReport
        validation = $validationReport
        artifacts = [ordered]@{ raw = $rawPath; import = $importPath; validation = $validationReportPath; report = $pipelineReportPath }
    }
    [IO.File]::WriteAllText($pipelineReportPath, (($pipelineReport | ConvertTo-Json -Depth 12) + "`n"), [Text.UTF8Encoding]::new($false))
    $pipelineReport | ConvertTo-Json -Depth 12
}
finally {
    Pop-Location
}