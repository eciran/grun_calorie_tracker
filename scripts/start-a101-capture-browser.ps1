param(
    [string]$CategoryUrl = "https://www.a101.com.tr/kapida/atistirmalik",
    [ValidateRange(1024, 65535)]
    [int]$Port = 9222
)

$ErrorActionPreference = "Stop"

$chromeCandidates = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)
$chrome = $chromeCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $chrome) {
    throw "Google Chrome bulunamadı."
}

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$profileDirectory = Join-Path $workspace "outputs\TR_Products\.a101-chrome-profile"
New-Item -ItemType Directory -Path $profileDirectory -Force | Out-Null

$arguments = @(
    "--remote-debugging-port=$Port",
    "--user-data-dir=$profileDirectory",
    "--no-first-run",
    "--no-default-browser-check",
    $CategoryUrl
)

Start-Process -FilePath $chrome -ArgumentList $arguments

Write-Host ""
Write-Host "A101 veri toplama Chrome'u açıldı."
Write-Host "1. Açılan pencerede A101 doğrulamasını tamamlayın."
Write-Host "2. Ürünlerin göründüğünden emin olun."
Write-Host "3. Bu Chrome penceresini açık bırakın."
Write-Host ""
Write-Host "Ardından scraperı --browser-mode cdp ile çalıştırın."
