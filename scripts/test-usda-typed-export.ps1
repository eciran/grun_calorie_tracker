param([Parameter(Mandatory=$true)] [string] $OutputDirectory)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath $OutputDirectory) { throw "Use a new replay output directory." }
$null = New-Item -ItemType Directory -Path $OutputDirectory
$replayState = @{ Mode="complete"; Requests=0 }
$passed = 0

# A process-local test double; the export is exercised without provider traffic.
function Invoke-RestMethod {
    param([string] $Uri, [string] $Method)
    if ($Method -ne "Get" -or $Uri -notlike "https://api.nal.usda.gov/fdc/v1/foods/search?*" -or
        $Uri -notlike "*api_key=LOCAL_TYPED_EXPORT_TEST*") { throw "Unexpected test request." }
    $replayState.Requests++
    $a = [pscustomobject]@{ nutrientId=1106; nutrientNumber="320"; nutrientName="Vitamin A, RAE"; unitName="UG"; value=6 }
    if ($replayState.Mode -eq "bad-unit") { $a.unitName = "IU" }
    if ($replayState.Mode -eq "legacy-number") { $a.nutrientId = $null }
    $nutrients = @(
        [pscustomobject]@{ nutrientId=1008; nutrientNumber="208"; nutrientName="Energy"; unitName="KCAL"; value=42 },
        [pscustomobject]@{ nutrientId=1003; nutrientNumber="203"; nutrientName="Protein"; unitName="G"; value=1 },
        [pscustomobject]@{ nutrientId=1004; nutrientNumber="204"; nutrientName="Total lipid (fat)"; unitName="G"; value=1 },
        [pscustomobject]@{ nutrientId=1005; nutrientNumber="205"; nutrientName="Carbohydrate"; unitName="G"; value=8 },
        [pscustomobject]@{ nutrientId=1104; nutrientNumber="318"; nutrientName="Vitamin A, IU"; unitName="IU"; value=20 },
        [pscustomobject]@{ nutrientId=1110; nutrientNumber="324"; nutrientName="Vitamin D (IU)"; unitName="IU"; value=12 },
        [pscustomobject]@{ nutrientId=1114; nutrientNumber="328"; nutrientName="Vitamin D"; unitName="UG"; value=0.3 },
        [pscustomobject]@{ nutrientId=1063; nutrientNumber="269.3"; nutrientName="Sugars, total"; unitName="G"; value=9 },
        [pscustomobject]@{ nutrientId=2000; nutrientNumber="269"; nutrientName="Total Sugars"; unitName="G"; value=5 }
    )
    if ($replayState.Mode -ne "iu-only") { $nutrients += $a }
    return [pscustomobject]@{ foods=@([pscustomobject]@{
        fdcId=123; description="Bananas, raw"; dataType="SR Legacy"; foodNutrients=$nutrients
    }) }
}

foreach ($mode in @("complete", "iu-only", "legacy-number", "bad-unit")) {
    $replayState.Mode = $mode
    $path = Join-Path $OutputDirectory "$mode.csv"
    if ($mode -eq "bad-unit") {
        $rejected = $false
        try {
            $null = & "$PSScriptRoot/export-usda-fooddata-generic-products.ps1" -ApiKey LOCAL_TYPED_EXPORT_TEST `
                -Queries @("banana raw") -OutputPath $path -PageSize 1 -MaxRowsPerQuery 1 -RequestDelayMs 0
        } catch { $rejected = $true }
        if (-not $rejected -or (Test-Path -LiteralPath $path)) { throw "Wrong-unit replay must not create an import CSV." }
        $passed++
        continue
    }
    $summary = & "$PSScriptRoot/export-usda-fooddata-generic-products.ps1" -ApiKey LOCAL_TYPED_EXPORT_TEST `
        -Queries @("banana raw") -OutputPath $path -PageSize 1 -MaxRowsPerQuery 1 -RequestDelayMs 0 | ConvertFrom-Json
    $rows = @(Import-Csv -LiteralPath $path)
    if ($summary.rowsWritten -ne 1 -or $rows.Count -ne 1) { throw "Exact one-row test export required." }
    $row = $rows[0]
    $proof = $row.nutrient_provenance_json | ConvertFrom-Json
    if ($proof.sourceKey -ne "USDA_FOODDATA:fdc:123" -or $proof.databaseUnitAttestation) { throw "Export provenance mismatch." }
    if ($row.vitamin_d -ne "0.3" -or $row.sugar -ne "5" -or $proof.fields.sugar.nutrientId -ne 2000) {
        throw "Typed D or sugar identity failed in actual CSV export."
    }
    if ($mode -eq "iu-only") {
        if ($row.vitamin_a -ne "" -or $null -ne $proof.fields.vitamin_a) { throw "IU-only source must not fill RAE." }
    } else {
        if ($row.vitamin_a -ne "6" -or $proof.fields.vitamin_a.nutrientId -ne 1106 -or
            $proof.fields.vitamin_a.canonicalUnit -ne "MCG_RAE" -or $proof.fields.vitamin_a.numericConversion) {
            throw "Exact RAE provenance did not survive CSV serialization."
        }
    }
    $passed++
}

if ($replayState.Requests -ne 4) { throw "Unexpected mocked request count." }
[pscustomobject]@{ passed=$passed; failed=0; mockedRequests=$replayState.Requests; providerRequests=0; databaseWrites=0 } | ConvertTo-Json
