param(
    [string]$QueuePath = ".\outputs\product-data-readiness\s9-tr-internet\tr-market-evidence-queue.tsv",
    [string]$CandidatesPath = ".\outputs\product-data-readiness\s9-tr-internet\tr-off-candidates.tsv",
    [string]$EvidencePath = "",
    [string]$OutputDir = ".\outputs\product-data-readiness\s9-tr-promotion"
)

$ErrorActionPreference = "Stop"
$pythonCandidates = @()
if ($env:PYTHON_EXECUTABLE) {
    $pythonCandidates += $env:PYTHON_EXECUTABLE
}
$pathPython = Get-Command python -ErrorAction SilentlyContinue
if ($pathPython -and $pathPython.Source -notlike "*\Microsoft\WindowsApps\*") {
    $pythonCandidates += $pathPython.Source
}
$bundledPython = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if (Test-Path -LiteralPath $bundledPython) {
    $pythonCandidates += $bundledPython
}
$python = $pythonCandidates | Select-Object -Unique | Select-Object -First 1
if (-not $python) {
    throw "Python is required. Set PYTHON_EXECUTABLE or install Python on PATH."
}

$arguments = @(
    (Join-Path $PSScriptRoot "assess_tr_market_evidence_queue.py"),
    "--queue", (Resolve-Path -LiteralPath $QueuePath).Path,
    "--candidates", (Resolve-Path -LiteralPath $CandidatesPath).Path,
    "--output-dir", $OutputDir
)
if ($EvidencePath) {
    $arguments += @("--evidence", (Resolve-Path -LiteralPath $EvidencePath).Path)
}

& $python @arguments
if ($LASTEXITCODE -ne 0) {
    throw "TR market evidence assessment failed with exit code $LASTEXITCODE."
}
