param(
    [Parameter(Mandatory = $true)][string] $JarPath,
    [string] $ImageTarPath,
    [ValidateSet("staging", "production")][string] $DeploymentEnvironment = "production",
    [string] $XsltReachabilityTestReport
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root "outputs/security-review"
$cache = Join-Path $root "target/security-scan-cache"
New-Item -ItemType Directory -Force -Path $output, $cache | Out-Null
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$assessment = $null
if ($XsltReachabilityTestReport) {
    if ($DeploymentEnvironment -ne "staging") { throw "Configuration assessment is staging-only." }
    $assessment = Get-Content (Join-Path $root "ops/security/xslt-staging-assessment.json") -Raw | ConvertFrom-Json
    if ([DateTimeOffset]::UtcNow -ge [DateTimeOffset]$assessment.reviewUntilUtc) { throw "XSLT assessment has expired." }
    [xml]$test = Get-Content -LiteralPath $XsltReachabilityTestReport -Raw
    if ($test.testsuite.name -ne "com.grun.calorietracker.config.ViewRenderingSecurityConfigTest" -or
        [int]$test.testsuite.failures -ne 0 -or [int]$test.testsuite.errors -ne 0 -or [int]$test.testsuite.skipped -ne 0) {
        throw "Passing XSLT reachability/guard tests are required."
    }
    foreach ($name in $assessment.requiredTests) {
        if (-not (@($test.testsuite.testcase) | Where-Object { $_.name -eq $name })) { throw "Missing assessment test: $name" }
    }
    # Bind the test evidence to every application class inside the actual scanned JAR.
    $classes = Join-Path (Split-Path (Split-Path (Resolve-Path $XsltReachabilityTestReport).Path)) "classes"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $entries = @($zip.Entries | Where-Object { $_.FullName -like "BOOT-INF/classes/com/grun/*.class" })
        if ($entries.Count -lt 100) { throw "Application class inventory was not found in the JAR." }
        foreach ($entry in $entries) {
            $local = Join-Path $classes ($entry.FullName.Substring("BOOT-INF/classes/".Length))
            if (-not (Test-Path -LiteralPath $local)) { throw "JAR and tested classes differ." }
            if ((Get-Item $local).LastWriteTimeUtc -gt (Get-Item $XsltReachabilityTestReport).LastWriteTimeUtc) { throw "Test evidence is stale." }
            $stream = $entry.Open()
            $sha = [System.Security.Cryptography.SHA256]::Create()
            try { $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace("-", "") }
            finally { $stream.Dispose(); $sha.Dispose() }
            if ($hash -ne (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash) { throw "JAR and tested classes differ: $($entry.FullName)" }
        }
    } finally { $zip.Dispose() }
}
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
    foreach ($finding in $findings | Where-Object { $_.Severity -in @("HIGH", "CRITICAL") }) {
        $reviewed = $assessment -and $finding.VulnerabilityID -eq $assessment.vulnerabilityId -and
            $finding.PkgName -eq $assessment.packageName -and $finding.InstalledVersion -eq $assessment.installedVersion
        if ($reviewed) {
            Write-Host "Retained finding evaluated as not affected by tested staging configuration: $($finding.VulnerabilityID). Review expires $($assessment.reviewUntilUtc)."
        } else { $blocked = $true }
    }
}
if ($blocked) { throw "Release gate blocked: high/critical findings require remediation or reviewed, time-bounded exception." }
if ($assessment) {
    @{ assessment = $assessment; jarSha256 = (Get-FileHash $jar -Algorithm SHA256).Hash;
       evidenceSha256 = (Get-FileHash $XsltReachabilityTestReport -Algorithm SHA256).Hash;
       checkedAtUtc = [DateTimeOffset]::UtcNow.ToString("o") } |
        ConvertTo-Json -Depth 10 | Set-Content (Join-Path $output "xslt-artifact-assessment.json") -Encoding utf8
}
Write-Host "Artifact vulnerability gate passed. This does not replace ownership, secret, native or AWS checks."
