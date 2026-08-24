param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$rows = @(Import-Csv -LiteralPath (Resolve-Path -LiteralPath $InputPath))
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$resolvedOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path

function Invoke-Pass([object[]]$InputRows, [hashtable]$State) {
    $inserted = 0
    $updated = 0
    foreach ($row in $InputRows) {
        if ($State.ContainsKey($row.restaurant_identity)) { $updated++ } else { $inserted++ }
        $State[$row.restaurant_identity] = $row
    }
    return [ordered]@{ input = $InputRows.Count; inserted = $inserted; updated = $updated; state = $State.Count }
}

$state = @{}
$pass1 = Invoke-Pass $rows $state
$canonical1 = @($state.Values | Sort-Object restaurant_identity)
$snapshot1 = Join-Path $resolvedOutput 'snapshot-pass1.csv'
$canonical1 | Export-Csv -NoTypeInformation -Encoding utf8 $snapshot1
$hash1 = (Get-FileHash -LiteralPath $snapshot1 -Algorithm SHA256).Hash

$pass2 = Invoke-Pass $rows $state
$canonical2 = @($state.Values | Sort-Object restaurant_identity)
$snapshot2 = Join-Path $resolvedOutput 'snapshot-pass2.csv'
$canonical2 | Export-Csv -NoTypeInformation -Encoding utf8 $snapshot2
$hash2 = (Get-FileHash -LiteralPath $snapshot2 -Algorithm SHA256).Hash

$report = [ordered]@{
    schema_version = 'restaurant-chain-rehearsal-v1'
    rows = $rows.Count
    unique_identities = @($rows.restaurant_identity | Select-Object -Unique).Count
    pass1 = $pass1
    pass2 = $pass2
    snapshot_hash_pass1 = $hash1
    snapshot_hash_pass2 = $hash2
    deterministic = $hash1 -eq $hash2
    idempotent = $pass1.inserted -eq $rows.Count -and $pass2.inserted -eq 0 -and $pass2.updated -eq $rows.Count -and $state.Count -eq $rows.Count
    db_writes = 0
}
$reportPath = Join-Path $resolvedOutput 'rehearsal-report.json'
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8
$report | ConvertTo-Json -Depth 6
if (-not $report.deterministic -or -not $report.idempotent) { throw 'Restaurant-chain rehearsal failed.' }
