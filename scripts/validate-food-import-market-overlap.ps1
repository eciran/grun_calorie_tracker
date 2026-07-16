param(
    [Parameter(Mandatory = $true)]
    [string[]] $InputPaths,

    [Parameter(Mandatory = $true)]
    [string] $ReportPath,

    [switch] $FailOnGate
)

$ErrorActionPreference = "Stop"

function Resolve-OutputFile {
    param([string] $Path)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    if ($parent) {
        return Join-Path (Resolve-Path -LiteralPath $parent).Path (Split-Path -Leaf $Path)
    }
    return Join-Path (Get-Location) $Path
}

function Normalize-Decimal {
    param([string] $Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $parsed = 0.0
    if (-not [double]::TryParse($Value.Replace(",", "."), [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)) {
        return "INVALID:$Value"
    }
    return $parsed.ToString("0.######", [Globalization.CultureInfo]::InvariantCulture)
}

if ($InputPaths.Count -lt 2) { throw "At least two market batch files are required." }
$resolvedInputs = @($InputPaths | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
$resolvedReport = Resolve-OutputFile -Path $ReportPath
$allRows = [System.Collections.Generic.List[object]]::new()
$marketCounts = [ordered]@{}

foreach ($input in $resolvedInputs) {
    foreach ($row in @(Import-Csv -LiteralPath $input -Encoding UTF8)) {
        $market = if ([string]::IsNullOrWhiteSpace($row.market_region)) { "UNSPECIFIED" } else { $row.market_region.Trim().ToUpperInvariant() }
        if (-not $marketCounts.Contains($market)) { $marketCounts[$market] = 0 }
        $marketCounts[$market]++
        $allRows.Add([pscustomobject]@{
            input = $input
            market = $market
            barcode = $row.barcode
            sourceKey = $row.source_key
            name = $row.name
            nutrition = @(
                (Normalize-Decimal $row.calories),
                (Normalize-Decimal $row.protein),
                (Normalize-Decimal $row.fat),
                (Normalize-Decimal $row.carbs)
            ) -join "|"
        }) | Out-Null
    }
}

$barcodeGroups = @($allRows | Where-Object { -not [string]::IsNullOrWhiteSpace($_.barcode) } | Group-Object barcode)
$overlaps = @($barcodeGroups | Where-Object { @($_.Group.market | Select-Object -Unique).Count -gt 1 })
$sourceKeyConflictGroups = @($overlaps | Where-Object { @($_.Group.sourceKey | Select-Object -Unique).Count -gt 1 })
$nutritionConflictGroups = @($overlaps | Where-Object { @($_.Group.nutrition | Select-Object -Unique).Count -gt 1 })
$nameVariantGroups = @($overlaps | Where-Object { @($_.Group.name | Select-Object -Unique).Count -gt 1 })
$pairCounts = [ordered]@{}
foreach ($group in $overlaps) {
    $pair = (@($group.Group.market | Select-Object -Unique | Sort-Object) -join "+")
    if (-not $pairCounts.Contains($pair)) { $pairCounts[$pair] = 0 }
    $pairCounts[$pair]++
}

$failures = [System.Collections.Generic.List[string]]::new()
if ($sourceKeyConflictGroups.Count -gt 0) { $failures.Add("CROSS_MARKET_SOURCE_KEY_CONFLICT") }
if ($nutritionConflictGroups.Count -gt 0) { $failures.Add("CROSS_MARKET_NUTRITION_CONFLICT") }

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    inputs = @($resolvedInputs | ForEach-Object { [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash } })
    rows = $allRows.Count
    uniqueBarcodes = $barcodeGroups.Count
    crossMarketOverlapGroups = $overlaps.Count
    rowsMergedByStableIdentity = @($overlaps | ForEach-Object { $_.Count - 1 } | Measure-Object -Sum).Sum
    gatePassed = $failures.Count -eq 0
    failures = @($failures)
    blocking = [ordered]@{
        sourceKeyConflictGroups = $sourceKeyConflictGroups.Count
        nutritionConflictGroups = $nutritionConflictGroups.Count
    }
    review = [ordered]@{
        nameVariantGroups = $nameVariantGroups.Count
    }
    marketCounts = $marketCounts
    overlapPairCounts = $pairCounts
    samples = @($overlaps | Select-Object -First 25 | ForEach-Object {
        [ordered]@{
            barcode = $_.Name
            markets = @($_.Group.market | Select-Object -Unique | Sort-Object)
            sourceKeys = @($_.Group.sourceKey | Select-Object -Unique)
            names = @($_.Group.name | Select-Object -Unique)
        }
    })
}

[IO.File]::WriteAllText($resolvedReport, (($report | ConvertTo-Json -Depth 8) + "`n"), [Text.UTF8Encoding]::new($false))
$report | ConvertTo-Json -Depth 8
if ($FailOnGate -and $failures.Count -gt 0) {
    throw "Cross-market overlap gate failed: $($failures -join ', ')"
}
