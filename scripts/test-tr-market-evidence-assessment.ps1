param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$targetRoot = Join-Path $projectRoot "target\tr-market-evidence-assessment-contract"
$resolvedProject = [IO.Path]::GetFullPath($projectRoot)
$resolvedTarget = [IO.Path]::GetFullPath($targetRoot)
if (-not $resolvedTarget.StartsWith($resolvedProject + [IO.Path]::DirectorySeparatorChar)) {
    throw "Unsafe test target path."
}
if (Test-Path -LiteralPath $resolvedTarget) {
    Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
}
New-Item -ItemType Directory -Path $resolvedTarget | Out-Null

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

$candidates = @"
barcode	name	brand	tier	marketScore	signals	issues	countryTags	stores	language	calories	protein	fat	carbs	sourceRow
8690000000012	Ulker Test	Ulker	STRICT	80	OFF_COUNTRY_TR		en:turkey		tr	100	1	2	3	1
8690000000029	Other Test	Other	STRICT	80	OFF_COUNTRY_TR		en:turkey		tr	100	1	2	3	2
"@
$queue = @"
barcode	name	brand	calories	protein	fat	carbs	offMarketScore	offSignals	evidenceStatus	requiredEvidence	evidenceSourceId	evidenceUrl	evidenceRetrievedAt	evidenceChecksum	reviewDecision
8690000000036	Cikolata Bar	Ulker	400	5	20	60	10	GS1_TR_PREFIX	PENDING_SECOND_SOURCE	AUTHORIZED_TR_RETAILER_OR_MANUFACTURER_MARKET_EVIDENCE					PENDING
8690000000043	Plain Product	New Brand	200	4	8	30	10	GS1_TR_PREFIX	PENDING_SECOND_SOURCE	AUTHORIZED_TR_RETAILER_OR_MANUFACTURER_MARKET_EVIDENCE					PENDING
8690000000050	123	123	100	1	2	3	10	GS1_TR_PREFIX	PENDING_SECOND_SOURCE	AUTHORIZED_TR_RETAILER_OR_MANUFACTURER_MARKET_EVIDENCE					PENDING
"@
$validHash = "a" * 64
$retrievedAt = (Get-Date).ToUniversalTime().ToString("o")
$evidence = @"
barcode	evidenceType	evidenceSourceId	evidenceUrl	evidenceRetrievedAt	evidenceChecksum	marketRegion	commercialUseAllowed	persistentStorageAllowed	reviewDecision	reviewerId
8690000000036	AUTHORIZED_MANUFACTURER_FEED	ULKER	https://example.com/products/8690000000036	$retrievedAt	$validHash	TR	true	true	APPROVED	admin-1
8690000000043	PUBLIC_WEB_PAGE	UNKNOWN	http://example.com/product	invalid	bad	TR	false	false	PENDING
"@

$utf8 = New-Object System.Text.UTF8Encoding($false)
$candidatePath = Join-Path $resolvedTarget "candidates.tsv"
$queuePath = Join-Path $resolvedTarget "queue.tsv"
$evidencePath = Join-Path $resolvedTarget "evidence.tsv"
[IO.File]::WriteAllText($candidatePath, $candidates.TrimStart(), $utf8)
[IO.File]::WriteAllText($queuePath, $queue.TrimStart(), $utf8)
[IO.File]::WriteAllText($evidencePath, $evidence.TrimStart(), $utf8)

& $python (Join-Path $PSScriptRoot "assess_tr_market_evidence_queue.py") `
    --queue $queuePath `
    --candidates $candidatePath `
    --evidence $evidencePath `
    --output-dir $resolvedTarget `
    --pilot-target 3 | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Assessment fixture failed."
}

$report = Get-Content (Join-Path $resolvedTarget "tr-market-evidence-assessment-report.json") -Raw -Encoding UTF8 | ConvertFrom-Json
if ($report.queueRows -ne 3 -or $report.strictBaseline -ne 2 -or $report.promotionCounts.PROMOTABLE -ne 1) {
    throw "Unexpected assessment counts."
}
if ($report.strictAfterPromotions -ne 3 -or $report.strictPilotPass -ne $true -or $report.catalogImportAllowed -ne $false) {
    throw "Promotion gate contract failed."
}
if ($report.promotionBlockers.MISSING_AUTHORIZED_EVIDENCE -ne 1 -or $report.promotionBlockers.UNSUPPORTED_EVIDENCE_TYPE -ne 1) {
    throw "Evidence blocker contract failed."
}

$rows = Import-Csv (Join-Path $resolvedTarget "tr-market-evidence-assessment.tsv") -Delimiter "`t"
$high = $rows | Where-Object barcode -eq "8690000000036"
$numeric = $rows | Where-Object barcode -eq "8690000000050"
if ($high.priorityLevel -ne "HIGH" -or $high.promotionStatus -ne "PROMOTABLE") {
    throw "High-priority authorized evidence row was not promotable."
}
if ($numeric.priorityLevel -ne "LOW" -or $numeric.promotionStatus -ne "BLOCKED") {
    throw "Suspicious identity row was not held back."
}

[pscustomobject]@{
    queueRows = $report.queueRows
    promotable = $report.promotionCounts.PROMOTABLE
    blocked = $report.promotionCounts.BLOCKED
    strictPilotPass = $report.strictPilotPass
    result = "PASS"
} | ConvertTo-Json
