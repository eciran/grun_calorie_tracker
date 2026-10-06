param(
    [Parameter(Mandatory = $true)][string] $JarPath,
    [string] $ImageTarPath
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root "outputs/security-review"
$cache = Join-Path $root "target/security-scan-cache"
New-Item -ItemType Directory -Force -Path $output, $cache | Out-Null
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$scanner = "aquasec/trivy@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c"
$mounts = @("--rm", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
    "-v", "${output}:/out", "-v", "${cache}:/cache")
docker run @mounts -v "${jar}:/artifact/app.jar:ro" $scanner rootfs --cache-dir /cache --no-progress --skip-version-check --disable-telemetry --scanners vuln --format json --output /out/java-artifact-gate.json /artifact
if ($LASTEXITCODE -ne 0) { throw "Artifact scanner failed; this is not a clean scan." }
$reports = @(Join-Path $output "java-artifact-gate.json")
if ($ImageTarPath) {
    $image = (Resolve-Path -LiteralPath $ImageTarPath).Path
    docker run @mounts -v "${image}:/input/runtime.tar:ro" $scanner image --cache-dir /cache --no-progress --skip-version-check --disable-telemetry --scanners vuln --input /input/runtime.tar --format json --output /out/image-artifact-gate.json
    if ($LASTEXITCODE -ne 0) { throw "Image scanner failed; this is not a clean scan." }
    $reports += Join-Path $output "image-artifact-gate.json"
}
$blocked = $false
foreach ($path in $reports) {
    $report = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if (-not $report.Results) { throw "Empty inventory in $path; review scanner coverage." }
    if ($path -like "*java-artifact-gate.json" -and -not ($report.Results | Where-Object { $_.Type -eq "jar" -and $_.Packages.Count -gt 0 })) {
        throw "JAR dependency inventory was not detected."
    }
    $findings = @($report.Results | ForEach-Object { $_.Vulnerabilities } | Where-Object { $_ })
    $findings | Group-Object Severity | Select-Object Name, Count | Format-Table
    if ($findings | Where-Object { $_.Severity -in @("HIGH", "CRITICAL") }) { $blocked = $true }
}
if ($blocked) { throw "Release gate blocked: high/critical findings require remediation or reviewed, time-bounded exception." }
Write-Host "Artifact vulnerability gate passed. This does not replace ownership, secret, native or AWS checks."
