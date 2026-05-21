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

    [switch] $RequireKnownNutriScore
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
        [string[]] $Names
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
        return $parsed.ToString([System.Globalization.CultureInfo]::InvariantCulture)
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
$seenBarcodes = [System.Collections.Generic.HashSet[string]]::new()
$grunRows = [System.Collections.Generic.List[object]]::new()

foreach ($row in (Import-Csv -LiteralPath $resolvedInput -Delimiter $Delimiter)) {
    $rowsRead++

    $barcode = Get-Barcode -Row $row
    $name = Get-TextValue -Row $row -Names @("product_name", "product_name_en", "generic_name", "name")
    $calories = Get-DecimalText -Row $row -Names @("energy-kcal_100g", "energy_kcal_100g", "calories")
    $protein = Get-DecimalText -Row $row -Names @("proteins_100g", "protein_100g", "protein")
    $fat = Get-DecimalText -Row $row -Names @("fat_100g", "fat")
    $carbs = Get-DecimalText -Row $row -Names @("carbohydrates_100g", "carbs_100g", "carbs")
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

    $grunRows.Add([pscustomobject]@{
            barcode = $barcode
            name = $name
            calories = $calories
            protein = $protein
            fat = $fat
            carbs = $carbs
            fiber = Get-DecimalText -Row $row -Names @("fiber_100g", "fiber")
            sugar = Get-DecimalText -Row $row -Names @("sugars_100g", "sugar")
            sodium = Get-DecimalText -Row $row -Names @("sodium_100g", "sodium")
            serving_size_grams = Get-ServingSizeGrams -Row $row
            serving_unit = Get-ServingUnit -Row $row
            image_url = $imageUrl
            external_image_url = $imageUrl
            allergens = Get-TextValue -Row $row -Names @("allergens_tags", "allergens")
            nutri_score = $nutriScore
        }) | Out-Null

    $rowsWritten++
    if ($rowsWritten -ge $Limit) {
        break
    }
}

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
    limit = $Limit
    delimiter = if ($Delimiter -eq "`t") { "TAB" } else { $Delimiter }
    requireCalories = $RequireCalories.IsPresent
    requireMacroData = $RequireMacroData.IsPresent
    requireImage = $RequireImage.IsPresent
    requireKnownNutriScore = $RequireKnownNutriScore.IsPresent
} | ConvertTo-Json
