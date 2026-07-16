param(
    [string] $ReadinessDirectory = ".\outputs\product-data-readiness",
    [string] $BundleDirectory = ".\outputs\product-data-readiness\staging-rehearsal",
    [ValidateRange(100, 10000)]
    [int] $MaxRowsPerChunk = 10000,
    [ValidateRange(1048576, 5767168)]
    [long] $MaxChunkBytes = 5767168
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredFile {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required staging artifact is missing: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Read-JsonFile {
    param([string] $Path)
    return Get-Content -Raw -Encoding UTF8 -LiteralPath (Resolve-RequiredFile $Path) | ConvertFrom-Json
}

function Get-CsvRowCount {
    param([string] $Path)
    return @(Import-Csv -Encoding UTF8 -LiteralPath (Resolve-RequiredFile $Path)).Count
}

function Assert-Equal {
    param($Expected, $Actual, [string] $Message)
    if ([string] $Expected -cne [string] $Actual) {
        throw "$Message Expected='$Expected', actual='$Actual'."
    }
}

function Assert-NoBlockingValues {
    param($Blocking, [string] $Market)
    foreach ($property in $Blocking.PSObject.Properties) {
        if ([int] $property.Value -ne 0) {
            throw "$Market validation has blocking issue '$($property.Name)'=$($property.Value)."
        }
    }
}

function Write-CsvChunks {
    param(
        [string] $SourcePath,
        [string] $Prefix,
        [string] $DestinationDirectory,
        [int] $RowsPerChunk,
        [long] $MaximumBytes
    )

    $rows = @(Import-Csv -Encoding UTF8 -LiteralPath $SourcePath)
    $records = [System.Collections.Generic.List[object]]::new()
    $writtenRows = 0
    for ($offset = 0; $offset -lt $rows.Count; $offset += $RowsPerChunk) {
        $chunkRows = @($rows | Select-Object -Skip $offset -First $RowsPerChunk)
        $sequence = [int] ($offset / $RowsPerChunk) + 1
        $chunkName = "{0}-part-{1:D3}.csv" -f $Prefix, $sequence
        $chunkPath = Join-Path $DestinationDirectory $chunkName
        $csvLines = @($chunkRows | ConvertTo-Csv -NoTypeInformation)
        [IO.File]::WriteAllLines($chunkPath, $csvLines, [Text.UTF8Encoding]::new($false))
        $size = (Get-Item -LiteralPath $chunkPath).Length
        if ($size -gt $MaximumBytes) {
            throw "Chunk '$chunkName' is $size bytes and exceeds the safe multipart limit of $MaximumBytes bytes."
        }
        $records.Add([ordered]@{
            file = "import-chunks/$chunkName"
            sequence = $sequence
            rows = $chunkRows.Count
            bytes = $size
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $chunkPath).Hash
        }) | Out-Null
        $writtenRows += $chunkRows.Count
    }
    Assert-Equal $rows.Count $writtenRows "Chunk row count differs from source artifact."
    return @($records)
}

$readinessRoot = (Resolve-Path -LiteralPath $ReadinessDirectory).Path
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$bundleParent = Split-Path -Parent $BundleDirectory
if (-not (Test-Path -LiteralPath $bundleParent)) {
    New-Item -ItemType Directory -Path $bundleParent | Out-Null
}
if (-not (Test-Path -LiteralPath $BundleDirectory)) {
    New-Item -ItemType Directory -Path $BundleDirectory | Out-Null
}
$bundleRoot = (Resolve-Path -LiteralPath $BundleDirectory).Path
$chunkRoot = Join-Path $bundleRoot "import-chunks"
if (-not (Test-Path -LiteralPath $chunkRoot)) { New-Item -ItemType Directory -Path $chunkRoot | Out-Null }
if (-not $bundleRoot.StartsWith($workspaceRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Bundle directory must remain inside the workspace."
}

$marketDefinitions = @(
    [pscustomobject]@{ Market = "UK_IE"; Slug = "uk-ie"; RequiredRows = 25000; AllowCapacityFailure = $false },
    [pscustomobject]@{ Market = "EU"; Slug = "eu"; RequiredRows = 25000; AllowCapacityFailure = $false },
    [pscustomobject]@{ Market = "TR"; Slug = "tr"; RequiredRows = 1600; AllowCapacityFailure = $true }
)

$artifactRecords = [System.Collections.Generic.List[object]]::new()
$totalBrandedRows = 0
foreach ($definition in $marketDefinitions) {
    $baseName = "open-food-facts-$($definition.Slug)-branded-v1-gate"
    $artifactPath = Resolve-RequiredFile (Join-Path $readinessRoot "$baseName-import.csv")
    $validationPath = Resolve-RequiredFile (Join-Path $readinessRoot "$baseName-validation.json")
    $reportPath = Resolve-RequiredFile (Join-Path $readinessRoot "$baseName-report.json")
    $validation = Read-JsonFile $validationPath
    $report = Read-JsonFile $reportPath
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash
    $rowCount = Get-CsvRowCount $artifactPath

    Assert-Equal $actualHash $validation.artifact.sha256 "$($definition.Market) artifact and validation checksum differ."
    Assert-Equal $actualHash $report.validation.artifact.sha256 "$($definition.Market) artifact and batch report checksum differ."
    Assert-Equal $rowCount $validation.rows "$($definition.Market) artifact and validation row count differ."
    Assert-Equal $definition.RequiredRows $rowCount "$($definition.Market) approved rehearsal row count differs."
    Assert-NoBlockingValues -Blocking $validation.blocking -Market $definition.Market

    if (-not $definition.AllowCapacityFailure -and -not [bool] $validation.gatePassed) {
        throw "$($definition.Market) validation gate is not green."
    }
    if ($definition.AllowCapacityFailure) {
        $unexpectedFailures = @($validation.failures | Where-Object { $_ -ne "ACCEPTED_ROWS_BELOW_TARGET" })
        if ($unexpectedFailures.Count -gt 0) {
            throw "TR has unexpected validation failures: $($unexpectedFailures -join ', ')."
        }
    }

    $bundleArtifact = Join-Path $bundleRoot ([IO.Path]::GetFileName($artifactPath))
    Copy-Item -LiteralPath $artifactPath -Destination $bundleArtifact -Force
    Copy-Item -LiteralPath $validationPath -Destination (Join-Path $bundleRoot ([IO.Path]::GetFileName($validationPath))) -Force
    Copy-Item -LiteralPath $reportPath -Destination (Join-Path $bundleRoot ([IO.Path]::GetFileName($reportPath))) -Force
    $chunks = Write-CsvChunks -SourcePath $artifactPath -Prefix $baseName -DestinationDirectory $chunkRoot -RowsPerChunk $MaxRowsPerChunk -MaximumBytes $MaxChunkBytes

    $artifactRecords.Add([ordered]@{
        role = "BRANDED_IMPORT"
        market = $definition.Market
        file = [IO.Path]::GetFileName($bundleArtifact)
        rows = $rowCount
        sha256 = $actualHash
        validationGatePassed = [bool] $validation.gatePassed
        acceptedCapacityException = [bool] $definition.AllowCapacityFailure
        chunks = @($chunks)
        warnings = $validation.warnings
    }) | Out-Null
    $totalBrandedRows += $rowCount
}

$genericSource = Resolve-RequiredFile (Join-Path $workspaceRoot "src\test\resources\generic-food-approved-seed-v1.csv")
$genericRows = Get-CsvRowCount $genericSource
Assert-Equal 146 $genericRows "Generic approved seed row count differs."
$genericHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $genericSource).Hash
$genericBundle = Join-Path $bundleRoot ([IO.Path]::GetFileName($genericSource))
Copy-Item -LiteralPath $genericSource -Destination $genericBundle -Force
$genericChunks = Write-CsvChunks -SourcePath $genericSource -Prefix "generic-food-approved-seed-v1" -DestinationDirectory $chunkRoot -RowsPerChunk $MaxRowsPerChunk -MaximumBytes $MaxChunkBytes
$artifactRecords.Add([ordered]@{
    role = "GENERIC_IMPORT"
    market = "GLOBAL"
    file = [IO.Path]::GetFileName($genericBundle)
    rows = $genericRows
    sha256 = $genericHash
    validationGatePassed = $true
    acceptedCapacityException = $false
    chunks = @($genericChunks)
}) | Out-Null

$overlapSource = Resolve-RequiredFile (Join-Path $readinessRoot "s10-cross-market-overlap-report.json")
$overlap = Read-JsonFile $overlapSource
if (-not [bool] $overlap.gatePassed) { throw "Cross-market overlap gate is not green." }
Assert-Equal $totalBrandedRows $overlap.rows "Cross-market report row count differs."
foreach ($input in $overlap.inputs) {
    $currentHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Resolve-RequiredFile $input.path)).Hash
    Assert-Equal $currentHash $input.sha256 "Cross-market report contains a stale artifact checksum."
}
Copy-Item -LiteralPath $overlapSource -Destination (Join-Path $bundleRoot ([IO.Path]::GetFileName($overlapSource))) -Force

$evidenceFiles = @(
    "src\test\resources\generic-food-manifest-v1.json",
    "src\test\resources\generic-food-source-selection-v1.json",
    "sample-data\manifests\open-food-facts-uk-ie-v1.json",
    "sample-data\manifests\open-food-facts-eu-v1.json",
    "sample-data\manifests\open-food-facts-tr-v1.json"
)
$evidenceRecords = [System.Collections.Generic.List[object]]::new()
foreach ($relativePath in $evidenceFiles) {
    $source = Resolve-RequiredFile (Join-Path $workspaceRoot $relativePath)
    $destination = Join-Path $bundleRoot ([IO.Path]::GetFileName($source))
    Copy-Item -LiteralPath $source -Destination $destination -Force
    $evidenceRecords.Add([ordered]@{
        file = [IO.Path]::GetFileName($destination)
        sourcePath = $relativePath.Replace("\", "/")
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash
    }) | Out-Null
}

$gitCommit = (& git -C $workspaceRoot rev-parse HEAD).Trim()
$gitBranch = (& git -C $workspaceRoot branch --show-current).Trim()
$dirtyPaths = @(& git -C $workspaceRoot status --short)
$latestMigration = Get-ChildItem -LiteralPath (Join-Path $workspaceRoot "src\main\resources\db\migration") -Filter "V*.sql" |
    ForEach-Object { if ($_.BaseName -match '^V(\d+)__') { [int] $Matches[1] } } |
    Measure-Object -Maximum |
    Select-Object -ExpandProperty Maximum

$manifest = [ordered]@{
    schemaVersion = 1
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    status = "READY_FOR_STAGING_REHEARSAL"
    productionApproved = $false
    sourceRevision = [ordered]@{
        branch = $gitBranch
        commit = $gitCommit
        workingTreeClean = $dirtyPaths.Count -eq 0
        dirtyPathCount = $dirtyPaths.Count
        latestFlywayMigration = "V$latestMigration"
    }
    counts = [ordered]@{
        brandedInputRows = $totalBrandedRows
        genericInputRows = $genericRows
        totalInputRows = $totalBrandedRows + $genericRows
        expectedUniqueBrandedIdentities = [int] $overlap.uniqueBarcodes
        expectedCanonicalFoodItems = [int] $overlap.uniqueBarcodes + $genericRows
        expectedCrossMarketMerges = [int] $overlap.rowsMergedByStableIdentity
    }
    integrity = [ordered]@{
        artifactsMatchValidation = $true
        chunksRespectMultipartLimit = $true
        maxChunkBytes = $MaxChunkBytes
        maxRowsPerChunk = $MaxRowsPerChunk
        crossMarketGatePassed = $true
        sourceKeyConflicts = [int] $overlap.blocking.sourceKeyConflictGroups
        nutritionConflicts = [int] $overlap.blocking.nutritionConflictGroups
        trCapacityExceptionRows = 1600
        trCapacityTarget = 25000
    }
    artifacts = @($artifactRecords)
    evidence = @($evidenceRecords)
    stagingAcceptance = [ordered]@{
        requiredFirstImportCanonicalCount = [int] $overlap.uniqueBarcodes + $genericRows
        requiredSecondImportNewItems = 0
        requiredSecondImportIdentityGrowth = 0
        snapshotBeforeImport = $true
        migrationGate = $true
        searchSmokeGate = $true
        rollbackRestoreGate = $true
    }
    blockers = @(
        "TR strict branded source capacity remains 1,600/25,000 and blocks production approval.",
        "A clean, committed application revision and immutable staging image are required before AWS execution.",
        "AWS staging is cost-paused and must be re-checked before any paid resource is started."
    )
}

$manifestPath = Join-Path $bundleRoot "bundle-manifest.json"
[IO.File]::WriteAllText($manifestPath, (($manifest | ConvertTo-Json -Depth 12) + "`n"), [Text.UTF8Encoding]::new($false))
$manifestHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash
[IO.File]::WriteAllText((Join-Path $bundleRoot "bundle-manifest.sha256"), "$manifestHash  bundle-manifest.json`n", [Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    bundleDirectory = $bundleRoot
    manifestPath = $manifestPath
    manifestSha256 = $manifestHash
    brandedRows = $totalBrandedRows
    genericRows = $genericRows
    totalRows = $totalBrandedRows + $genericRows
    expectedCanonicalFoodItems = [int] $overlap.uniqueBarcodes + $genericRows
    expectedCrossMarketMerges = [int] $overlap.rowsMergedByStableIdentity
    workingTreeClean = $dirtyPaths.Count -eq 0
    dirtyPathCount = $dirtyPaths.Count
    readyForStagingRehearsal = $true
    productionApproved = $false
} | ConvertTo-Json -Depth 5