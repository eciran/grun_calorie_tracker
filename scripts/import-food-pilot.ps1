param(
    [string]$ApiBaseUrl = "http://localhost:8080",
    [string]$Token = $env:GRUN_ADMIN_JWT,
    [string]$FilePath = "sample-data/food-products-uk-openfoodfacts-small.csv",
    [string]$ImportMode = "RAW_EXTERNAL",
    [string]$ImportFormat = "AUTO"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Admin JWT token is required. Pass -Token or set GRUN_ADMIN_JWT."
}

$resolvedFile = Resolve-Path -LiteralPath $FilePath
$uri = "$ApiBaseUrl/api/v1/admin/products/import?importMode=$ImportMode&importFormat=$ImportFormat"

if ($PSVersionTable.PSVersion.Major -ge 7) {
    $response = Invoke-RestMethod `
        -Uri $uri `
        -Method Post `
        -Headers @{ Authorization = "Bearer $Token" } `
        -Form @{ file = Get-Item -LiteralPath $resolvedFile.Path }

    $response | ConvertTo-Json -Depth 8
    return
}

$curl = Get-Command curl.exe -ErrorAction SilentlyContinue
if ($null -eq $curl) {
    throw "PowerShell 7+ or curl.exe is required for multipart upload on this machine."
}

& $curl.Source `
    -s `
    -X POST `
    $uri `
    -H "Authorization: Bearer $Token" `
    -F "file=@$($resolvedFile.Path);type=text/csv"
