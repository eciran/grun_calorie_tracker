param(
    [string]$RegistryPath = ".\sample-data\manifests\tr-food-source-registry-v1.json"
)

$ErrorActionPreference = "Stop"
$registryFile = (Resolve-Path -LiteralPath $RegistryPath).Path
$registry = Get-Content -LiteralPath $registryFile -Raw -Encoding UTF8 | ConvertFrom-Json

if ($registry.schemaVersion -ne 1 -or $registry.marketRegion -ne "TR") {
    throw "Unexpected TR source registry contract."
}

$sources = @($registry.sources)
$ids = @($sources | ForEach-Object { $_.id })
if (($ids | Select-Object -Unique).Count -ne $ids.Count) {
    throw "Source registry contains duplicate ids."
}

$productionSources = @($sources | Where-Object { $_.classification -eq "PRODUCTION_ALLOWED" })
if ($productionSources.Count -ne 1 -or $productionSources[0].id -ne "OPEN_FOOD_FACTS") {
    throw "Only Open Food Facts may be production-allowed in the internet-only phase."
}

foreach ($source in $sources) {
    if ($source.directImport -and $source.classification -ne "PRODUCTION_ALLOWED") {
        throw "Non-production source permits direct import: $($source.id)"
    }
    if ($source.directImport -and ($source.commercialUse -ne $true -or $source.persistentStorage -ne $true)) {
        throw "Direct-import source lacks commercial/persistence approval: $($source.id)"
    }
}

$signals = @($registry.marketEvidence.signals)
$storePolicy = $registry.marketEvidence.storePolicy
$exactStores = @($storePolicy.exactTurkeySpecific | ForEach-Object { $_.ToLowerInvariant() })
$ambiguousStores = @($storePolicy.ambiguousRequiresIndependentEvidence | ForEach-Object { $_.ToLowerInvariant() })
if ($exactStores.Count -eq 0 -or $ambiguousStores.Count -eq 0) {
    throw "TR store evidence policy must define exact and ambiguous retailers."
}
if (@($exactStores | Where-Object { $ambiguousStores -contains $_ }).Count -gt 0) {
    throw "A retailer cannot be both exact TR evidence and ambiguous evidence."
}
if ($exactStores -contains "migros" -or $exactStores -contains "metro") {
    throw "Ambiguous international chains cannot be exact TR market evidence."
}

$prefix = $signals | Where-Object { $_.id -eq "GS1_TR_PREFIX" }
if ($null -eq $prefix -or $prefix.strong -ne $false -or [int]$prefix.score -ge [int]$registry.marketEvidence.thresholds.review) {
    throw "GS1 TR prefix must remain weak evidence."
}

$country = $signals | Where-Object { $_.id -eq "OFF_COUNTRY_TR" }
if ($null -eq $country -or $country.strong -ne $true -or [int]$country.score -lt [int]$registry.marketEvidence.thresholds.strict) {
    throw "Explicit OFF Turkey country evidence must satisfy the strict threshold."
}

$turkomp = $sources | Where-Object { $_.id -eq "TURKOMP" }
if ($null -eq $turkomp -or $turkomp.directImport -ne $false -or $turkomp.classification -ne "REJECTED_CURRENT_SCOPE") {
    throw "TurKomp must stay excluded until a commercial contract exists."
}

[pscustomobject]@{
    registryId = $registry.registryId
    sourceCount = $sources.Count
    productionSources = @($productionSources.id)
    strictThreshold = [int]$registry.marketEvidence.thresholds.strict
    reviewThreshold = [int]$registry.marketEvidence.thresholds.review
    result = "PASS"
} | ConvertTo-Json -Depth 5
