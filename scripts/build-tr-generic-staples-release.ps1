param(
    [string]$BaseSeedPath = ".\src\test\resources\generic-food-approved-seed-v1.csv",
    [string]$StaplesSeedPath = ".\outputs\tr-generic-staples-approved-v1.csv",
    [string]$OutputPath = ".\outputs\tr-generic-staples-release-v1.csv",
    [string]$ReportPath = ".\outputs\tr-generic-staples-release-v1.json"
)

$ErrorActionPreference = "Stop"
$incorrectOliveOilSourceKey = "USDA_FOODDATA:fdc:171443"
$baseRows = @(Import-Csv -LiteralPath $BaseSeedPath | Where-Object { $_.source_key -ne $incorrectOliveOilSourceKey })
$stapleRows = @(Import-Csv -LiteralPath $StaplesSeedPath)
$rows = @($baseRows + $stapleRows)

$duplicateKeys = @($rows | Group-Object source_key | Where-Object Count -gt 1)
if ($duplicateKeys.Count -gt 0) { throw "Duplicate source keys: $($duplicateKeys.Name -join ', ')" }
if ($baseRows.Count -ne 145) { throw "Expected 145 retained base rows, got $($baseRows.Count)." }
if ($stapleRows.Count -ne 13) { throw "Expected 13 staple rows, got $($stapleRows.Count)." }

$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$report = [ordered]@{
    status = "PASS"
    rows = $rows.Count
    retainedBaseRows = $baseRows.Count
    addedStapleRows = $stapleRows.Count
    removedIncorrectRows = 1
    removedSourceKeys = @($incorrectOliveOilSourceKey)
    source = "USDA FoodData Central"
    nutritionBasis = "SOURCE_REPORTED"
    outputSha256 = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportPath -Encoding utf8
$report | ConvertTo-Json -Depth 5
