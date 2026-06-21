param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [ValidateRange(1, 1000000)]
    [int] $Limit = 500,

    [string] $Delimiter = "`t",

    [switch] $RequireCalories,

    [switch] $RequireMacroData,

    [switch] $RequireImage,

    [switch] $RequireKnownNutriScore,

    [ValidateSet("UK_IE", "TR", "EU", "GLOBAL")]
    [string] $MarketRegion = "GLOBAL",

    [string[]] $CountryTerms = @(),

    [string[]] $PriorityStoreTerms = @(),

    [ValidateRange(0, 100)]
    [int] $PriorityStoreTargetPercent = 60,

    [ValidateRange(1, 100000000)]
    [int] $MaxRowsToRead = 1000000
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredFile {
    param([string] $Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Input file was not found: $Path"
    }

    return (Resolve-Path -LiteralPath $Path).Path
}

function Resolve-OutputFile {
    param([string] $Path)

    $parent = Split-Path -Parent $Path
    if ($parent) {
        if (-not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Path $parent | Out-Null
        }
        return Join-Path (Resolve-Path -LiteralPath $parent).Path (Split-Path -Leaf $Path)
    }

    return Join-Path (Get-Location) $Path
}

function Get-TextValue {
    param(
        [pscustomobject] $Row,
        [string[]] $Names
    )

    foreach ($name in $Names) {
        $property = $Row.PSObject.Properties[$name]
        if ($null -eq $property) {
            continue
        }

        $value = [string] $property.Value
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
    }

    return $null
}

function Get-DecimalText {
    param(
        [pscustomobject] $Row,
        [string[]] $Names,
        [int] $DecimalPlaces = 1
    )

    $value = Get-TextValue -Row $Row -Names $Names
    if ($null -eq $value) {
        return $null
    }

    $normalized = $value.Replace(",", ".")
    $parsed = 0.0
    if ([double]::TryParse(
            $normalized,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref] $parsed
        )) {
        $rounded = [Math]::Round($parsed, $DecimalPlaces, [MidpointRounding]::AwayFromZero)
        return $rounded.ToString("0." + ("#" * $DecimalPlaces), [System.Globalization.CultureInfo]::InvariantCulture)
    }

    return $null
}

function Get-Barcode {
    param([pscustomobject] $Row)

    $barcode = Get-TextValue -Row $Row -Names @("code", "barcode")
    if ($null -eq $barcode) {
        return $null
    }

    $normalized = ($barcode -replace "\s", "").Trim()
    if ($normalized -notmatch "^\d{8,14}$") {
        return $null
    }

    return $normalized
}

function Test-AnyTermMatch {
    param(
        [string] $Value,
        [string[]] $Terms
    )

    if ($null -eq $Terms -or $Terms.Count -eq 0) {
        return $true
    }

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    $normalizedValue = $Value.ToLowerInvariant()
    foreach ($term in $Terms) {
        if ([string]::IsNullOrWhiteSpace($term)) {
            continue
        }
        if ($normalizedValue.Contains($term.Trim().ToLowerInvariant())) {
            return $true
        }
    }

    return $false
}

function Get-RegionCountryTerms {
    param([string] $Region)

    if ($CountryTerms.Count -gt 0) {
        return $CountryTerms
    }

    switch ($Region) {
        "UK_IE" { return @("united kingdom", "en:united-kingdom", "ireland", "en:ireland") }
        "TR" { return @("turkey", "turkiye", "türkiye", "en:turkey") }
        "EU" { return @("european union", "europe", "france", "germany", "spain", "italy", "netherlands", "belgium", "poland", "ireland", "united kingdom") }
        default { return @() }
    }
}

function Get-RegionPriorityStoreTerms {
    param([string] $Region)

    if ($PriorityStoreTerms.Count -gt 0) {
        return $PriorityStoreTerms
    }

    switch ($Region) {
        "UK_IE" { return @("tesco", "dunnes", "dunnes stores") }
        default { return @() }
    }
}

function Get-ServingSizeGrams {
    param([pscustomobject] $Row)

    $servingQuantity = Get-DecimalText -Row $Row -Names @("serving_quantity", "serving_size_value")
    $servingUnit = Get-TextValue -Row $Row -Names @("serving_quantity_unit", "serving_size_unit")

    if ($servingQuantity -and ($null -eq $servingUnit -or $servingUnit -match "^(g|gram|grams|ml|milliliter|milliliters)$")) {
        return $servingQuantity
    }

    $servingSize = Get-TextValue -Row $Row -Names @("serving_size")
    if ($servingSize -and $servingSize -match "(\d+(?:[.,]\d+)?)\s*(g|gr|gram|grams|ml)") {
        return $Matches[1].Replace(",", ".")
    }

    return $null
}

function Get-ServingUnit {
    param([pscustomobject] $Row)

    $servingUnit = Get-TextValue -Row $Row -Names @("serving_quantity_unit", "serving_size_unit")
    if ($servingUnit -match "^(ml|milliliter|milliliters)$") {
        return "MILLILITER"
    }

    return "GRAM"
}

$resolvedInput = Resolve-RequiredFile -Path $InputPath
$resolvedOutput = Resolve-OutputFile -Path $OutputPath
$rowsRead = 0
$rowsSkipped = 0
$rowsWritten = 0
$priorityRowsWritten = 0
$fallbackRowsWritten = 0
$countryFilteredRows = 0
$priorityCandidateRows = 0
$seenBarcodes = [System.Collections.Generic.HashSet[string]]::new()
$priorityRows = [System.Collections.Generic.List[object]]::new()
$fallbackRows = [System.Collections.Generic.List[object]]::new()
$regionCountryTerms = Get-RegionCountryTerms -Region $MarketRegion
$regionPriorityStoreTerms = Get-RegionPriorityStoreTerms -Region $MarketRegion
$priorityTarget = [int][Math]::Ceiling($Limit * ($PriorityStoreTargetPercent / 100.0))

foreach ($row in (Import-Csv -LiteralPath $resolvedInput -Delimiter $Delimiter)) {
    $rowsRead++
    if ($rowsRead -gt $MaxRowsToRead) {
        break
    }

    $barcode = Get-Barcode -Row $row
    $name = Get-TextValue -Row $row -Names @("product_name", "product_name_en", "generic_name", "name")
    $calories = Get-DecimalText -Row $row -Names @("energy-kcal_100g", "energy_kcal_100g", "calories") -DecimalPlaces 0
    $protein = Get-DecimalText -Row $row -Names @("proteins_100g", "protein_100g", "protein") -DecimalPlaces 1
    $fat = Get-DecimalText -Row $row -Names @("fat_100g", "fat") -DecimalPlaces 1
    $carbs = Get-DecimalText -Row $row -Names @("carbohydrates_100g", "carbs_100g", "carbs") -DecimalPlaces 1
    $imageUrl = Get-TextValue -Row $row -Names @("image_url", "image_front_url")
    $nutriScore = Get-TextValue -Row $row -Names @("nutrition_grade_fr", "nutriscore_grade", "nutri_score")

    if ($null -eq $barcode -or $null -eq $name) {
        $rowsSkipped++
        continue
    }

    if (-not $seenBarcodes.Add($barcode)) {
        $rowsSkipped++
        continue
    }

    if ($RequireCalories -and $null -eq $calories) {
        $rowsSkipped++
        continue
    }

    if ($RequireMacroData -and $null -eq $protein -and $null -eq $fat -and $null -eq $carbs) {
        $rowsSkipped++
        continue
    }

    if ($RequireImage -and $null -eq $imageUrl) {
        $rowsSkipped++
        continue
    }

    if ($RequireKnownNutriScore -and ($null -eq $nutriScore -or $nutriScore -match "^(unknown|not-applicable)$")) {
        $rowsSkipped++
        continue
    }

    $countryValue = Get-TextValue -Row $row -Names @("countries_tags", "countries_en", "countries", "country")
    if (-not (Test-AnyTermMatch -Value $countryValue -Terms $regionCountryTerms)) {
        $countryFilteredRows++
        $rowsSkipped++
        continue
    }

    $storeValue = Get-TextValue -Row $row -Names @("stores_tags", "stores", "retailers", "retailer")
    $isPriorityStore = $regionPriorityStoreTerms.Count -gt 0 -and (Test-AnyTermMatch -Value $storeValue -Terms $regionPriorityStoreTerms)
    if ($isPriorityStore) {
        $priorityCandidateRows++
    }

    $grunRow = [pscustomobject]@{
            catalog_type = "BRANDED_PRODUCT"
            data_source = "OPEN_FOOD_FACTS"
            barcode = $barcode
            source_key = "barcode:$barcode"
            name = $name
            brand = Get-TextValue -Row $row -Names @("brands", "brand")
            calories = $calories
            protein = $protein
            fat = $fat
            carbs = $carbs
            fiber = Get-DecimalText -Row $row -Names @("fiber_100g", "fiber")
            sugar = Get-DecimalText -Row $row -Names @("sugars_100g", "sugar")
            sodium = Get-DecimalText -Row $row -Names @("sodium_100g", "sodium")
            serving_size_grams = Get-ServingSizeGrams -Row $row
            serving_unit = Get-ServingUnit -Row $row
            market_region = $MarketRegion
            image_url = $imageUrl
            external_image_url = $imageUrl
            display_image_url = $null
            allergens = Get-TextValue -Row $row -Names @("allergens_tags", "allergens")
            nutri_score = $nutriScore
        }

    if ($isPriorityStore -and $priorityRows.Count -lt $priorityTarget) {
        $priorityRows.Add($grunRow) | Out-Null
    } elseif ($fallbackRows.Count -lt $Limit) {
        $fallbackRows.Add($grunRow) | Out-Null
    } elseif ($priorityRows.Count -ge $priorityTarget) {
        break
    }
}

$grunRows = [System.Collections.Generic.List[object]]::new()
foreach ($priorityRow in $priorityRows) {
    if ($grunRows.Count -ge $Limit) {
        break
    }
    $grunRows.Add($priorityRow) | Out-Null
    $priorityRowsWritten++
}
foreach ($fallbackRow in $fallbackRows) {
    if ($grunRows.Count -ge $Limit) {
        break
    }
    $grunRows.Add($fallbackRow) | Out-Null
    $fallbackRowsWritten++
}
$rowsWritten = $grunRows.Count

if ($rowsWritten -eq 0) {
    throw "No valid rows were produced. Check OFF headers, filter switches, and delimiter."
}

$csvLines = $grunRows | ConvertTo-Csv -NoTypeInformation
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($resolvedOutput, $csvLines, $utf8WithoutBom)

[pscustomobject]@{
    input = $resolvedInput
    output = $resolvedOutput
    rowsRead = $rowsRead
    rowsWritten = $rowsWritten
    rowsSkipped = $rowsSkipped
    countryFilteredRows = $countryFilteredRows
    priorityCandidateRows = $priorityCandidateRows
    priorityRowsWritten = $priorityRowsWritten
    fallbackRowsWritten = $fallbackRowsWritten
    limit = $Limit
    maxRowsToRead = $MaxRowsToRead
    delimiter = if ($Delimiter -eq "`t") { "TAB" } else { $Delimiter }
    requireCalories = $RequireCalories.IsPresent
    requireMacroData = $RequireMacroData.IsPresent
    requireImage = $RequireImage.IsPresent
    requireKnownNutriScore = $RequireKnownNutriScore.IsPresent
    marketRegion = $MarketRegion
    countryTerms = $regionCountryTerms
    priorityStoreTerms = $regionPriorityStoreTerms
    priorityStoreTargetPercent = $PriorityStoreTargetPercent
} | ConvertTo-Json
