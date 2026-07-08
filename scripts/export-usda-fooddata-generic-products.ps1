param(
    [string] $ApiKey = $env:USDA_FOODDATA_API_KEY,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [string[]] $Queries = @(
        "chicken breast raw",
        "chicken breast cooked",
        "egg whole raw",
        "rice white raw",
        "rice white cooked",
        "oats raw",
        "broccoli raw",
        "potato boiled",
        "banana raw",
        "apple raw"
    ),

    [ValidateSet("UK_IE", "TR", "EU", "GLOBAL")]
    [string] $MarketRegion = "GLOBAL",

    [ValidateRange(1, 25)]
    [int] $PageSize = 5,

    [ValidateRange(0, 10000)]
    [int] $RequestDelayMs = 250,

    [string[]] $DataTypes = @("Foundation", "SR Legacy"),

    [string] $BaseUrl = "https://api.nal.usda.gov/fdc/v1/foods/search"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "USDA FoodData Central API key is required. Pass -ApiKey or set USDA_FOODDATA_API_KEY."
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

function Get-NutrientValue {
    param(
        $Food,
        [string[]] $Numbers,
        [string[]] $Names,
        [int] $DecimalPlaces = 1
    )

    if ($null -eq $Food.foodNutrients) {
        return $null
    }

    foreach ($nutrient in $Food.foodNutrients) {
        $number = [string] $nutrient.nutrientNumber
        $name = ([string] $nutrient.nutrientName).ToLowerInvariant()
        $matchesNumber = $Numbers -contains $number
        $matchesName = $false
        foreach ($expectedName in $Names) {
            if ($name -eq $expectedName.ToLowerInvariant() -or $name.Contains($expectedName.ToLowerInvariant())) {
                $matchesName = $true
                break
            }
        }

        if ($matchesNumber -or $matchesName) {
            $value = 0.0
            if ([double]::TryParse(
                    ([string] $nutrient.value).Replace(',', '.'),
                    [System.Globalization.NumberStyles]::Float,
                    [System.Globalization.CultureInfo]::InvariantCulture,
                    [ref] $value
                )) {
                $rounded = [Math]::Round($value, $DecimalPlaces, [MidpointRounding]::AwayFromZero)
                return $rounded.ToString("0." + ("#" * $DecimalPlaces), [System.Globalization.CultureInfo]::InvariantCulture)
            }
        }
    }

    return $null
}

function Get-PreparationState {
    param([string] $Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return "UNSPECIFIED"
    }

    $value = $Text.ToLowerInvariant()
    if ($value.Contains("raw") -or $value.Contains("uncooked")) { return "RAW" }
    if ($value.Contains("boiled")) { return "BOILED" }
    if ($value.Contains("grilled")) { return "GRILLED" }
    if ($value.Contains("fried")) { return "FRIED" }
    if ($value.Contains("baked")) { return "BAKED" }
    if ($value.Contains("roasted")) { return "ROASTED" }
    if ($value.Contains("steamed")) { return "STEAMED" }
    if ($value.Contains("cooked")) { return "COOKED" }
    return "UNSPECIFIED"
}

function Get-CleanDescription {
    param([string] $Description)

    if ([string]::IsNullOrWhiteSpace($Description)) {
        return $null
    }

    $clean = $Description.Trim() -replace "\s+", " "
    return $clean
}

$resolvedOutput = Resolve-OutputFile -Path $OutputPath
$seenFdcIds = [System.Collections.Generic.HashSet[string]]::new()
$rows = [System.Collections.Generic.List[object]]::new()
$encodedDataTypes = [System.Uri]::EscapeDataString(($DataTypes -join ","))

foreach ($query in $Queries) {
    $encodedQuery = [System.Uri]::EscapeDataString($query)
    $uri = "$BaseUrl`?api_key=$ApiKey&query=$encodedQuery&dataType=$encodedDataTypes&pageSize=$PageSize&pageNumber=1&sortBy=dataType.keyword&sortOrder=asc"
    $result = Invoke-RestMethod -Uri $uri -Method Get

    if ($null -eq $result.foods) {
        continue
    }

    foreach ($food in $result.foods) {
        $fdcId = [string] $food.fdcId
        if ([string]::IsNullOrWhiteSpace($fdcId) -or -not $seenFdcIds.Add($fdcId)) {
            continue
        }

        $description = Get-CleanDescription -Description ([string] $food.description)
        if ($null -eq $description) {
            continue
        }

        $rows.Add([pscustomobject]@{
            catalog_type = "GENERIC_INGREDIENT"
            data_source = "USDA_FOODDATA"
            fdc_id = $fdcId
            source_key = "USDA_FOODDATA:fdc:$fdcId"
            name = $description
            calories = Get-NutrientValue -Food $food -Numbers @("208", "1008") -Names @("Energy") -DecimalPlaces 0
            protein = Get-NutrientValue -Food $food -Numbers @("203", "1003") -Names @("Protein")
            fat = Get-NutrientValue -Food $food -Numbers @("204", "1004") -Names @("Total lipid", "Total fat")
            carbs = Get-NutrientValue -Food $food -Numbers @("205", "1005") -Names @("Carbohydrate")
            fiber = Get-NutrientValue -Food $food -Numbers @("291", "1079") -Names @("Fiber")
            sugar = Get-NutrientValue -Food $food -Numbers @("269", "2000") -Names @("Sugars")
            sodium = Get-NutrientValue -Food $food -Numbers @("307", "1093") -Names @("Sodium")
            potassium = Get-NutrientValue -Food $food -Numbers @("306", "1092") -Names @("Potassium")
            cholesterol = Get-NutrientValue -Food $food -Numbers @("601", "1253") -Names @("Cholesterol")
            calcium = Get-NutrientValue -Food $food -Numbers @("301", "1087") -Names @("Calcium")
            iron = Get-NutrientValue -Food $food -Numbers @("303", "1089") -Names @("Iron")
            magnesium = Get-NutrientValue -Food $food -Numbers @("304", "1090") -Names @("Magnesium")
            zinc = Get-NutrientValue -Food $food -Numbers @("309", "1095") -Names @("Zinc")
            vitamin_a = Get-NutrientValue -Food $food -Numbers @("318", "1104") -Names @("Vitamin A")
            vitamin_c = Get-NutrientValue -Food $food -Numbers @("401", "1162") -Names @("Vitamin C")
            vitamin_d = Get-NutrientValue -Food $food -Numbers @("328", "1114") -Names @("Vitamin D")
            vitamin_e = Get-NutrientValue -Food $food -Numbers @("323", "1109") -Names @("Vitamin E")
            vitamin_b12 = Get-NutrientValue -Food $food -Numbers @("418", "1178") -Names @("Vitamin B-12", "Vitamin B12")
            serving_size_grams = "100"
            serving_unit = "g"
            market_region = $MarketRegion
            preparation_state = Get-PreparationState -Text ($query + " " + $description)
            alias_en = $query
            source_note = "USDA FoodData Central; dataType=$($food.dataType); query=$query"
        }) | Out-Null
    }

    if ($RequestDelayMs -gt 0) {
        Start-Sleep -Milliseconds $RequestDelayMs
    }
}

if ($rows.Count -eq 0) {
    throw "No USDA FoodData rows were exported. Check API key, queries, and data types."
}

$csvLines = $rows | ConvertTo-Csv -NoTypeInformation
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($resolvedOutput, $csvLines, $utf8WithoutBom)

[pscustomobject]@{
    outputPath = $resolvedOutput
    rowsWritten = $rows.Count
    queries = $Queries
    marketRegion = $MarketRegion
    dataTypes = $DataTypes
    pageSize = $PageSize
} | ConvertTo-Json -Depth 5