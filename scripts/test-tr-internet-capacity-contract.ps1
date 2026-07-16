param(
    [string]$ManifestPath = ".\sample-data\manifests\open-food-facts-tr-internet-discovery-v1.json"
)

$ErrorActionPreference = "Stop"
$manifest = Get-Content -LiteralPath (Resolve-Path $ManifestPath) -Raw -Encoding UTF8 | ConvertFrom-Json
$capacity = $manifest.capacity
$tierSum = [int]$capacity.strict + [int]$capacity.review + [int]$capacity.quarantine

if ($tierSum -ne [int]$capacity.uniqueCandidates) {
    throw "TR candidate tier sum does not match unique candidate count."
}
if ($manifest.gates.strict5k -ne "FAIL_CAPACITY" -or $manifest.gates.strict25k -ne "FAIL_CAPACITY") {
    throw "TR gates must remain failed until strict capacity is actually available."
}

$potentialAfterAllEvidence = [int]$capacity.strict + [int]$capacity.qualityPassGtinPrefixWithoutCountryEvidence
$pilotEvidenceNeeded = 5000 - [int]$capacity.strict
$gateGapAfterAllEvidence = 25000 - $potentialAfterAllEvidence
if ($pilotEvidenceNeeded -le 0 -or $gateGapAfterAllEvidence -le 0) {
    throw "TR capacity contract no longer matches the recorded blocked state."
}

[pscustomobject]@{
    manifestId = $manifest.manifestId
    tierSum = $tierSum
    strict = [int]$capacity.strict
    evidenceQueue = [int]$capacity.qualityPassGtinPrefixWithoutCountryEvidence
    potentialAfterAllEvidence = $potentialAfterAllEvidence
    pilotEvidenceNeeded = $pilotEvidenceNeeded
    gateGapAfterAllEvidence = $gateGapAfterAllEvidence
    result = "PASS"
} | ConvertTo-Json
