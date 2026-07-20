param(
    [string] $ManifestPath = ".\src\test\resources\generic-food-manifest-v1.json",
    [string] $OutputPath = ".\sample-data\usda-foundation-foods-queries.txt",
    [switch] $Check
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Generic food manifest was not found: $ManifestPath"
}

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
$queries = @(
    $manifest.entries |
        ForEach-Object { $_.variants } |
        ForEach-Object { ([string] $_.query).Trim() } |
        Where-Object { $_ } |
        Select-Object -Unique
)

if ($queries.Count -eq 0) {
    throw "Generic food manifest produced no USDA queries."
}

if ($Check) {
    if (-not (Test-Path -LiteralPath $OutputPath)) {
        throw "Generated USDA query file was not found: $OutputPath"
    }
    $current = @(
        Get-Content -LiteralPath $OutputPath |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -and -not $_.StartsWith("#") }
    )
    if (($current -join [Environment]::NewLine) -cne ($queries -join [Environment]::NewLine)) {
        throw "USDA query file is stale. Run scripts/generate-generic-food-manifest-queries.ps1."
    }
} else {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }
    $lines = @(
        "# Generated from generic-food-manifest-v1.json."
        "# Do not edit manually; update the manifest and regenerate."
        ""
    ) + $queries
    [System.IO.File]::WriteAllLines(
        (Join-Path (Resolve-Path -LiteralPath $parent).Path (Split-Path -Leaf $OutputPath)),
        $lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

[pscustomobject]@{
    manifestVersion = $manifest.version
    queryCount = $queries.Count
    mode = if ($Check) { "CHECK" } else { "WRITE" }
    outputPath = $OutputPath
} | ConvertTo-Json