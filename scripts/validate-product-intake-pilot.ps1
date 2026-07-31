param(
    [Parameter(Mandatory = $true)][string]$EvidencePath,
    [string]$ThresholdsPath = "ops/product-intake-pilot/pilot-thresholds.json",
    [switch]$AllowSynthetic,
    [switch]$AllowDeferredProviderProof
)
$ErrorActionPreference = "Stop"
$evidence = Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json
$thresholds = Get-Content -LiteralPath $ThresholdsPath -Raw | ConvertFrom-Json
$failures = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function RequiredNumber($value, [string]$name) {
    if ($null -eq $value) { $failures.Add("$name is not measured"); return $false }
    return $true
}
function RequireTrue($value, [string]$name) {
    if ($value -ne $true) { $failures.Add("$name must be proven") }
}
function RequireReference($value, [string]$name) {
    if ([string]::IsNullOrWhiteSpace($value)) { $failures.Add("$name is required") }
}

if ($evidence.schemaVersion -ne $thresholds.schemaVersion) { $failures.Add("schemaVersion mismatch") }
if ($evidence.evidenceType -eq "SYNTHETIC_TEST" -and -not $AllowSynthetic) {
    $failures.Add("synthetic evidence cannot approve a real pilot")
}
if ($null -eq $evidence.capturedAt) { $failures.Add("capturedAt is required") }
if ($evidence.marketRegions.Count -lt 1) { $failures.Add("at least one configured market is required") }
if ($evidence.rolloutStage -notin @(1, 10, 50, 100)) { $failures.Add("rolloutStage must be 1, 10, 50 or 100") }

if ($evidence.ocrLanguages.Count -lt $thresholds.ocr.minimumLanguages) {
    $failures.Add("OCR requires at least $($thresholds.ocr.minimumLanguages) measured languages")
}
foreach ($language in $evidence.ocrLanguages) {
    $prefix = "ocr[$($language.language)]"
    if ($language.sampleCount -lt $thresholds.ocr.minimumSamplesPerLanguage) { $failures.Add("$prefix sampleCount below threshold") }
    if ($language.criticalFieldPrecision -lt $thresholds.ocr.minimumCriticalFieldPrecision) { $failures.Add("$prefix criticalFieldPrecision below threshold") }
    if ($language.criticalFieldCoverage -lt $thresholds.ocr.minimumCriticalFieldCoverage) { $failures.Add("$prefix criticalFieldCoverage below threshold") }
    if ($language.basisAccuracy -lt $thresholds.ocr.minimumBasisAccuracy) { $failures.Add("$prefix basisAccuracy below threshold") }
    if ($language.saltSodiumConfusionRate -gt $thresholds.ocr.maximumSaltSodiumConfusionRate) { $failures.Add("$prefix saltSodiumConfusionRate above threshold") }
    if ($language.decimalAccuracy -lt $thresholds.ocr.minimumDecimalAccuracy) { $failures.Add("$prefix decimalAccuracy below threshold") }
    if ($language.correctionRate -gt $thresholds.ocr.maximumCorrectionRate) { $failures.Add("$prefix correctionRate above threshold") }
    if ($language.p95LatencyMs -gt $thresholds.ocr.maximumP95LatencyMs) { $failures.Add("$prefix p95LatencyMs above threshold") }
    if ($language.crashAnrRate -gt $thresholds.ocr.maximumCrashAnrRate) { $failures.Add("$prefix crashAnrRate above threshold") }
}

if ((RequiredNumber $evidence.operations.queueP95Hours "operations.queueP95Hours") -and $evidence.operations.queueP95Hours -gt $thresholds.operations.maximumQueueP95Hours) { $failures.Add("operations.queueP95Hours above threshold") }
if ((RequiredNumber $evidence.operations.overdueCases "operations.overdueCases") -and $evidence.operations.overdueCases -gt $thresholds.operations.maximumOverdueCases) { $failures.Add("operations.overdueCases above threshold") }
if ((RequiredNumber $evidence.operations.capacityUtilization "operations.capacityUtilization") -and $evidence.operations.capacityUtilization -gt $thresholds.operations.maximumCapacityUtilization) { $failures.Add("operations.capacityUtilization above threshold") }
if ((RequiredNumber $evidence.operations.uploadErrorRate "operations.uploadErrorRate") -and $evidence.operations.uploadErrorRate -gt $thresholds.operations.maximumUploadErrorRate) { $failures.Add("operations.uploadErrorRate above threshold") }

if ((RequiredNumber $evidence.retention.deletionFailures "retention.deletionFailures") -and $evidence.retention.deletionFailures -gt $thresholds.retention.maximumDeletionFailures) { $failures.Add("retention.deletionFailures above threshold") }
if ((RequiredNumber $evidence.retention.deletionBacklog "retention.deletionBacklog") -and $evidence.retention.deletionBacklog -gt $thresholds.retention.maximumDeletionBacklog) { $failures.Add("retention.deletionBacklog above threshold") }
if ((RequiredNumber $evidence.retention.oldestEvidenceAgeDays "retention.oldestEvidenceAgeDays") -and $evidence.retention.oldestEvidenceAgeDays -gt $thresholds.retention.maximumEvidenceAgeDays) { $failures.Add("retention.oldestEvidenceAgeDays above threshold") }
RequireReference $evidence.retention.rehearsalReference "retention.rehearsalReference"

foreach ($field in @("internalProductLeakage", "evidenceAuthorizationFailures", "unexplainedDuplicateCandidates", "materialNutritionMisapplies")) {
    $value = $evidence.safety.$field
    if ((RequiredNumber $value "safety.$field") -and $value -gt 0) { $failures.Add("safety.$field is a pilot stop condition") }
}
RequireTrue $evidence.abuse.reviewCompleted "abuse.reviewCompleted"
if ((RequiredNumber $evidence.abuse.confirmedAbuseRate "abuse.confirmedAbuseRate") -and $evidence.abuse.confirmedAbuseRate -gt $thresholds.abuse.maximumConfirmedAbuseRate) { $failures.Add("abuse.confirmedAbuseRate above threshold") }
RequireReference $evidence.abuse.reportReference "abuse.reportReference"

if ((RequiredNumber $evidence.cost.estimatedCostPerAcceptedContributionEur "cost.estimatedCostPerAcceptedContributionEur") -and $evidence.cost.estimatedCostPerAcceptedContributionEur -gt $thresholds.cost.maximumCostPerAcceptedContributionEur) { $failures.Add("cost.estimatedCostPerAcceptedContributionEur above threshold") }
RequireReference $evidence.cost.reportReference "cost.reportReference"

RequireTrue $evidence.rehearsals.killSwitchBlockedNewIntakes "rehearsals.killSwitchBlockedNewIntakes"
RequireTrue $evidence.rehearsals.rollbackRestoredPreviousStage "rehearsals.rollbackRestoredPreviousStage"
RequireTrue $evidence.rehearsals.secondMarketEnabledWithoutCodeChange "rehearsals.secondMarketEnabledWithoutCodeChange"
RequireReference $evidence.rehearsals.reference "rehearsals.reference"

if (-not $evidence.provider.policyValidated) { $failures.Add("provider policy validation is required") }
if (-not $evidence.provider.externalProofArchived) {
    if ($AllowDeferredProviderProof) { $warnings.Add("external provider proof remains a release gate") }
    else { $failures.Add("external provider proof is required") }
}

$result = [ordered]@{
    status = if ($failures.Count -eq 0) { if ($warnings.Count -eq 0) { "PASS" } else { "PASS_WITH_RELEASE_GATE" } } else { "BLOCKED" }
    rolloutStage = $evidence.rolloutStage
    marketRegions = @($evidence.marketRegions)
    failures = $failures
    warnings = $warnings
}
$result | ConvertTo-Json -Depth 6
if ($failures.Count -gt 0) { exit 2 }
