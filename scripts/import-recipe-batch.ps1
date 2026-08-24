param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [Parameter(Mandatory = $true)]
    [string]$File,

    [string]$BaseUrl = "http://localhost:8080"
)

if (-not (Test-Path -LiteralPath $File)) {
    throw "Recipe import file not found: $File"
}

$resolvedFile = (Resolve-Path -LiteralPath $File).Path
$body = Get-Content -LiteralPath $resolvedFile -Raw
$uri = "$BaseUrl/api/v1/admin/recipes/imports"

Invoke-RestMethod `
    -Uri $uri `
    -Method Post `
    -Headers @{ Authorization = "Bearer $Token" } `
    -ContentType "application/json" `
    -Body $body