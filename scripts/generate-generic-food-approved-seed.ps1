param(
    [string] $ManifestPath = ".\src\test\resources\generic-food-manifest-v1.json",
    [string] $CandidateCsvPath = ".\src\test\resources\generic-food-usda-candidates-v1.csv",
    [string] $SeedOutputPath = ".\src\test\resources\generic-food-approved-seed-v1.csv",
    [string] $SourceSelectionOutputPath = ".\src\test\resources\generic-food-source-selection-v1.json",
    [switch] $Check
)

$ErrorActionPreference = "Stop"
$utf8 = [Text.UTF8Encoding]::new($false)

function Resolve-ExistingFile([string] $Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required file was not found: $Path" }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-OutputFile([string] $Path) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    if ($parent) { return Join-Path (Resolve-Path -LiteralPath $parent).Path (Split-Path -Leaf $Path) }
    return Join-Path (Get-Location) $Path
}

function Csv([object] $Value) {
    if ($null -eq $Value) { return '""' }
    return '"' + ([string] $Value).Replace('"', '""') + '"'
}

function Join-Unique([object[]] $Values) {
    return (@($Values | ForEach-Object { ([string] $_).Trim() } | Where-Object { $_ } | Select-Object -Unique) -join ';')
}

function Display-Name([string] $BaseName, [string] $State, [string] $Language) {
    $prefixes = if ($Language -eq 'TR') {
        @{ RAW='Çiğ'; COOKED='Pişmiş'; BOILED='Haşlanmış'; GRILLED='Izgara'; FRIED='Kızartılmış'; BAKED='Fırınlanmış'; ROASTED='Fırınlanmış'; STEAMED='Buharda'; PREPARED='Hazırlanmış'; UNSPECIFIED='' }
    } else {
        @{ RAW='Raw'; COOKED='Cooked'; BOILED='Boiled'; GRILLED='Grilled'; FRIED='Fried'; BAKED='Baked'; ROASTED='Roasted'; STEAMED='Steamed'; PREPARED='Prepared'; UNSPECIFIED='' }
    }
    $prefix = $prefixes[$State]
    if ($null -eq $prefix) { throw "Unsupported preparation state: $State" }
    if ([string]::IsNullOrWhiteSpace($prefix)) { return $BaseName }
    return "$prefix $BaseName"
}

$manifestFile = Resolve-ExistingFile $ManifestPath
$candidateFile = Resolve-ExistingFile $CandidateCsvPath
$manifest = [IO.File]::ReadAllText($manifestFile, [Text.Encoding]::UTF8) | ConvertFrom-Json
$candidates = @(Import-Csv -LiteralPath $candidateFile -Encoding UTF8)
$candidateByQuery = @{}
foreach ($candidate in $candidates) {
    $query = ([string] $candidate.alias_en).Trim()
    if (-not $query) { throw "Candidate row is missing alias_en/query for source key $($candidate.source_key)" }
    if ($candidateByQuery.ContainsKey($query)) { throw "Duplicate candidate query: $query" }
    $candidateByQuery[$query] = $candidate
}

$seedRows = [Collections.Generic.List[object]]::new()
$selections = [Collections.Generic.List[object]]::new()
$seenSourceKeys = [Collections.Generic.HashSet[string]]::new()
$expectedQueries = [Collections.Generic.HashSet[string]]::new()
foreach ($entry in $manifest.entries) {
    foreach ($variant in $entry.variants) {
        $query = ([string] $variant.query).Trim()
        [void] $expectedQueries.Add($query)
        if (-not $candidateByQuery.ContainsKey($query)) { throw "No approved USDA candidate for manifest query: $query" }
        $source = $candidateByQuery[$query]
        if (-not $seenSourceKeys.Add([string] $source.source_key)) { throw "Duplicate USDA source key: $($source.source_key)" }
        foreach ($field in @('calories', 'protein', 'fat', 'carbs')) {
            $numericValue = 0.0
            $rawValue = [string] $source.$field
            $parsed = [double]::TryParse($rawValue, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $numericValue)
            if (-not $parsed -or [double]::IsNaN($numericValue) -or [double]::IsInfinity($numericValue) -or $numericValue -lt 0) {
                throw "Core nutrition field $field is invalid for $query"
            }
            if (($field -eq 'calories' -and $numericValue -gt 1000) -or ($field -ne 'calories' -and $numericValue -gt 100)) {
                throw "Core nutrition field $field is outside the allowed range for $query"
            }
        }

        $baseEn = [string] $entry.displayNames.EN
        $baseTr = [string] $entry.displayNames.TR
        $state = [string] $variant.state
        $displayEn = Display-Name $baseEn $state 'EN'
        $displayTr = Display-Name $baseTr $state 'TR'
        $servingJson = '[' + ($variant.serving | ConvertTo-Json -Depth 8 -Compress) + ']'
        $servingAmount = if ($null -ne $variant.serving.gramWeight) { $variant.serving.gramWeight } else { $variant.serving.mlVolume }
        $servingUnit = if ($null -ne $variant.serving.gramWeight) { 'g' } else { 'ml' }
        $dataType = if ([string] $source.source_note -match 'dataType=([^;]+)') { $Matches[1] } else { 'UNKNOWN' }
        $fdcId = ([string] $source.source_key).Split(':')[-1]

        $seedRows.Add([ordered]@{
            catalog_type='GENERIC_INGREDIENT'; data_source='USDA_FOODDATA'; source_key=$source.source_key
            name=$source.name; display_name=$displayEn; short_display_name=$displayEn
            calories=$source.calories; protein=$source.protein; fat=$source.fat; carbs=$source.carbs
            fiber=$source.fiber; sugar=$source.sugar; sodium=$source.sodium; potassium=$source.potassium
            cholesterol=$source.cholesterol; calcium=$source.calcium; iron=$source.iron; magnesium=$source.magnesium
            zinc=$source.zinc; vitamin_a=$source.vitamin_a; vitamin_c=$source.vitamin_c; vitamin_d=$source.vitamin_d
            vitamin_e=$source.vitamin_e; vitamin_b12=$source.vitamin_b12
            serving_size_grams=$servingAmount; serving_unit=$servingUnit; market_region='GLOBAL'
            preparation_state=$state; nutrition_basis='SOURCE_REPORTED'
            alias_en=(Join-Unique (@($entry.aliases.EN) + @($query))); alias_tr=(Join-Unique @($entry.aliases.TR))
            source_note=$source.source_note; display_name_en=$displayEn; short_display_name_en=$displayEn
            display_name_tr=$displayTr; short_display_name_tr=$displayTr; serving_options_json=$servingJson
        }) | Out-Null
        $selections.Add([ordered]@{
            identity=$entry.identity; category=$entry.category; expectedState=$state; query=$query
            fdcId=$fdcId; sourceKey=$source.source_key; selectedName=$source.name; selectedDataType=$dataType
            selectedPreparationState=$source.preparation_state; nutritionBasis='SOURCE_REPORTED'
        }) | Out-Null
    }
}

if ($candidateByQuery.Count -ne $expectedQueries.Count) {
    $unexpected = @($candidateByQuery.Keys | Where-Object { -not $expectedQueries.Contains($_) })
    throw "Candidate query count differs from manifest. Unexpected: $($unexpected -join ', ')"
}
if ($seedRows.Count -ne 146 -or $selections.Count -ne 146) { throw "Expected 146 variants, got $($seedRows.Count)" }

$headers = @($seedRows[0].Keys)
$csvLines = [Collections.Generic.List[string]]::new()
$csvLines.Add((@($headers | ForEach-Object { Csv $_ }) -join ',')) | Out-Null
foreach ($row in $seedRows) { $csvLines.Add((@($headers | ForEach-Object { Csv $row[$_] }) -join ',')) | Out-Null }
$csv = ($csvLines -join "`r`n") + "`r`n"
$selectionDocument = [ordered]@{
    version='generic-food-source-selection-v1'; manifestVersion=$manifest.version
    generatedFrom='USDA FoodData Central API candidate export'; selectionCount=$selections.Count; entries=$selections
}
$selectionJson = $selectionDocument | ConvertTo-Json -Depth 10
foreach ($text in @($csv, $selectionJson)) {
    if ($text -match '[ÃÂÆâƒÅÄ]') { throw 'Generated artifact contains mojibake.' }
}

$seedOutput = Resolve-OutputFile $SeedOutputPath
$selectionOutput = Resolve-OutputFile $SourceSelectionOutputPath
if ($Check) {
    if (-not (Test-Path -LiteralPath $seedOutput) -or [IO.File]::ReadAllText($seedOutput, [Text.Encoding]::UTF8) -ne $csv) { throw "Approved seed is stale: $seedOutput" }
    if (-not (Test-Path -LiteralPath $selectionOutput) -or [IO.File]::ReadAllText($selectionOutput, [Text.Encoding]::UTF8) -ne $selectionJson) { throw "Source selection is stale: $selectionOutput" }
} else {
    [IO.File]::WriteAllText($seedOutput, $csv, $utf8)
    [IO.File]::WriteAllText($selectionOutput, $selectionJson, $utf8)
}

[pscustomobject]@{
    manifestVersion=$manifest.version; variants=$seedRows.Count; uniqueSourceKeys=$seenSourceKeys.Count
    seedOutput=$seedOutput; sourceSelectionOutput=$selectionOutput; mode=$(if ($Check) { 'CHECK' } else { 'WRITE' })
} | ConvertTo-Json