param(
    [string]$InputPath = ".\outputs\openfoodfacts-products.csv.gz",
    [string]$RegistryPath = ".\sample-data\manifests\tr-food-source-registry-v1.json",
    [string]$OutputDirectory = ".\outputs\product-data-readiness\s9-tr-internet",
    [ValidateRange(1, 100000000)]
    [int]$MaxRowsToRead = 10000000,
    [ValidateRange(0, 10000000)]
    [int]$ProgressEvery = 250000
)

$ErrorActionPreference = "Stop"
$python = if ($env:PYTHON_EXECUTABLE) {
    $env:PYTHON_EXECUTABLE
} else {
    $command = Get-Command python -ErrorAction SilentlyContinue
    if ($command) { $command.Source } else { $null }
}

if (-not $python) {
    throw "Python 3 is required. Set PYTHON_EXECUTABLE to the Python executable path."
}

$scriptPath = Join-Path $PSScriptRoot "analyze_tr_open_food_facts_capacity.py"
& $python $scriptPath `
    --input $InputPath `
    --registry $RegistryPath `
    --output-dir $OutputDirectory `
    --max-rows $MaxRowsToRead `
    --progress-every $ProgressEvery

if ($LASTEXITCODE -ne 0) {
    throw "TR OFF capacity analyzer failed with exit code $LASTEXITCODE."
}
