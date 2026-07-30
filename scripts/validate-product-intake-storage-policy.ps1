param(
    [string]$CorsPolicy = "ops/product-intake-storage/cors-policy.json",
    [string]$LifecyclePolicy = "ops/product-intake-storage/lifecycle-policy.json",
    [int]$MaximumEvidenceDays = 90,
    [int]$MaximumPendingDays = 30
)
$ErrorActionPreference = "Stop"
$cors = Get-Content -LiteralPath $CorsPolicy -Raw | ConvertFrom-Json
$lifecycle = Get-Content -LiteralPath $LifecyclePolicy -Raw | ConvertFrom-Json
if (-not $cors.allowedOrigins -or $cors.allowedOrigins -contains "*") { throw "CORS origins must be explicit and non-wildcard." }
$unexpectedMethods = @($cors.allowedMethods | Where-Object { $_ -notin @("PUT", "GET", "HEAD") })
if ($unexpectedMethods.Count -gt 0) { throw "CORS contains unsupported methods: $($unexpectedMethods -join ', ')" }
if ($cors.maxAgeSeconds -gt 600) { throw "CORS maxAgeSeconds must not exceed the upload URL TTL baseline (600)." }
$pending = $lifecycle.rules | Where-Object id -eq "product-intake-pending-expiry"
$evidence = $lifecycle.rules | Where-Object id -eq "product-intake-evidence-hard-backstop"
$multipart = $lifecycle.rules | Where-Object id -eq "product-intake-aborted-multipart"
if (-not $pending.enabled -or $pending.expireAfterDays -gt $MaximumPendingDays) { throw "Pending lifecycle exceeds $MaximumPendingDays days." }
if (-not $evidence.enabled -or $evidence.expireAfterDays -gt $MaximumEvidenceDays) { throw "Evidence lifecycle exceeds $MaximumEvidenceDays days." }
if (-not $multipart.enabled -or $multipart.abortIncompleteMultipartAfterDays -gt 1) { throw "Incomplete multipart uploads must be aborted within one day." }
[pscustomobject]@{ status="VALID"; origins=$cors.allowedOrigins.Count; pendingDays=$pending.expireAfterDays; evidenceDays=$evidence.expireAfterDays; abortMultipartDays=$multipart.abortIncompleteMultipartAfterDays } | ConvertTo-Json -Compress