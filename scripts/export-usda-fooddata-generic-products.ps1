param(
    [string] $ApiKey = $env:USDA_FOODDATA_API_KEY,

    [string] $OutputPath = ".\outputs\usda-generic-foods.csv",

    [string] $QueryFile,

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
    [int] $PageSize = 25,

    [ValidateRange(1, 10)]
    [int] $MaxRowsPerQuery = 1,

    [ValidateRange(0, 10000)]
    [int] $RequestDelayMs = 250,

    [string[]] $DataTypes = @("Foundation", "SR Legacy"),

    [string] $BaseUrl = "https://api.nal.usda.gov/fdc/v1/foods/search",

    [switch] $RunRuleTests
)

$ErrorActionPreference = "Stop"


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
        [string[]] $NutrientIds = @(),
        [string[]] $Names,
        [int] $DecimalPlaces = 1
    )

    if ($null -eq $Food.foodNutrients) {
        return $null
    }

    foreach ($nutrient in $Food.foodNutrients) {
        $number = [string] $nutrient.nutrientNumber
        $name = ([string] $nutrient.nutrientName).ToLowerInvariant()
        $nutrientId = [string] $nutrient.nutrientId
        $matchesNumber = $Numbers -contains $number
        $matchesNutrientId = $NutrientIds -contains $nutrientId
        $matchesName = $false
        foreach ($expectedName in $Names) {
            if ($name -eq $expectedName.ToLowerInvariant() -or $name.Contains($expectedName.ToLowerInvariant())) {
                $matchesName = $true
                break
            }
        }

        if ($matchesNumber -or $matchesNutrientId -or $matchesName) {
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
    if ($value.Contains("canned")) { return "PREPARED" }
    if ($value.Contains("brewed")) { return "PREPARED" }
    if ($value.Contains("cooked") -or $value.Contains("braised") -or $value.Contains("stewed") -or $value.Contains("simmered") -or $value.Contains("scrambled") -or $value.Contains("dry heat")) {
        return "COOKED"
    }
    return "UNSPECIFIED"
}

function Test-PreparationStateMatchesQuery {
    param(
        [string] $Query,
        [string] $Description
    )

    $requestedState = Get-PreparationState -Text $Query
    if ($requestedState -eq "UNSPECIFIED") {
        return $true
    }

    $actualState = Get-PreparationState -Text $Description
    if ($requestedState -eq "RAW") {
        return $actualState -eq "RAW"
    }
    if ($requestedState -eq "COOKED") {
        return $actualState -in @("COOKED", "BOILED", "GRILLED", "FRIED", "BAKED", "ROASTED", "STEAMED")
    }

    return $actualState -eq $requestedState
}
function Get-CleanDescription {
    param([string] $Description)

    if ([string]::IsNullOrWhiteSpace($Description)) {
        return $null
    }

    $clean = $Description.Trim() -replace "\s+", " "
    return $clean
}


function Get-NormalizedText {
    param([string] $Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    return ($Text.ToLowerInvariant() -replace "[^a-z0-9\s]", " " -replace "\s+", " ").Trim()
}

function Test-DescriptionMatchesQuery {
    param(
        [string] $Query,
        [string] $Description
    )

    $normalizedQuery = Get-NormalizedText -Text $Query
    $normalizedDescription = Get-NormalizedText -Text $Description
    if ($null -eq $normalizedQuery -or $null -eq $normalizedDescription) {
        return $false
    }
    if (-not (Test-PreparationStateMatchesQuery -Query $Query -Description $Description)) {
        return $false
    }

    $queryExclusionTerms = @{
        "banana raw" = @("pepper", "peppers", "hungarian wax", "overripe")
        "apple raw" = @("juice", "sauce", "pie filling", "babyfood")
        "egg whole raw" = @("substitute", "powder", "dried", "yolk only", "white only")
        "chicken breast raw" = @("lunchmeat", "deli", "breaded", "nugget", "patty", "sausage", "soup", "broth", "bouillon", "skin", "wing", "thigh", "drumstick")
        "chicken breast cooked" = @("lunchmeat", "deli", "breaded", "fried", "nugget", "patty", "sausage", "soup", "broth", "bouillon", "skin", "wing", "thigh", "drumstick")
        "rice white raw" = @("flour", "pasta", "noodle", "noodles", "mix", "pilaf", "pudding", "cake", "snack", "cereal", "babyfood", "restaurant")
        "rice white cooked" = @("flour", "pasta", "noodle", "noodles", "mix", "pilaf", "pudding", "cake", "snack", "cereal", "babyfood", "restaurant")
        "rice brown raw" = @("flour", "pasta", "noodle", "noodles", "mix", "pilaf", "pudding", "cake", "snack", "cereal", "babyfood", "restaurant")
        "rice brown cooked" = @("flour", "pasta", "noodle", "noodles", "mix", "pilaf", "pudding", "cake", "snack", "cereal", "babyfood", "restaurant")
        "milk whole 3 25 milkfat" = @("cheese", "feta", "yogurt", "buttermilk", "dry", "powder")
        "milk skim" = @("cheese", "feta", "yogurt", "buttermilk", "dry", "powder")
        "chicken thigh cooked" = @("breaded", "fried", "nugget", "patty")
        "water" = @("convolvulus", "spinach", "vegetable")
        "sunflower oil" = @("industrial", "seed", "seeds", "kernel", "kernels", "roasted")
    }

    if ($queryExclusionTerms.ContainsKey($normalizedQuery)) {
        foreach ($term in $queryExclusionTerms[$normalizedQuery]) {
            $pattern = "(^| )$([regex]::Escape($term))( |$)"
            if ($normalizedDescription -match $pattern) {
                return $false
            }
        }
    }

    $queryRequiredPrefixes = @{
        "banana raw" = @("banana", "bananas")
        "apple raw" = @("apple", "apples")
        "milk whole 3 25 milkfat" = @("milk")
        "milk skim" = @("milk")
    }

    if ($queryRequiredPrefixes.ContainsKey($normalizedQuery)) {
        $matchesPrefix = $false
        foreach ($prefix in $queryRequiredPrefixes[$normalizedQuery]) {
            if ($normalizedDescription.StartsWith($prefix + " ") -or $normalizedDescription -eq $prefix) {
                $matchesPrefix = $true
                break
            }
        }
        if (-not $matchesPrefix) {
            return $false
        }
    }

    $ignoredQueryTerms = @(
        "raw", "cooked", "boiled", "grilled", "fried", "baked", "roasted", "steamed"
    )
    $terms = $normalizedQuery.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries) |
        Where-Object { $ignoredQueryTerms -notcontains $_ }

    foreach ($term in $terms) {
        if (-not $normalizedDescription.Contains($term)) {
            return $false
        }
    }

    return $true
}

function Get-DescriptionRelevanceScore {
    param(
        [string] $Query,
        [string] $Description,
        [string] $DataType
    )

    $normalizedQuery = Get-NormalizedText -Text $Query
    $normalizedDescription = Get-NormalizedText -Text $Description
    if ($null -eq $normalizedQuery -or $null -eq $normalizedDescription) {
        return -1000
    }

    $score = 0
    if ($normalizedDescription -eq $normalizedQuery) { $score += 100 }
    if ($normalizedDescription.StartsWith($normalizedQuery + " ")) { $score += 60 }

    $preferredTerms = @{
        "chicken breast raw" = @("chicken", "breast", "raw")
        "chicken breast cooked" = @("chicken", "breast", "cooked")
        "rice white raw" = @("rice", "white")
        "rice white cooked" = @("rice", "white", "cooked")
        "rice brown raw" = @("rice", "brown")
        "rice brown cooked" = @("rice", "brown", "cooked")
    }

    if ($preferredTerms.ContainsKey($normalizedQuery)) {
        foreach ($term in $preferredTerms[$normalizedQuery]) {
            if ($normalizedDescription -match "(^| )$([regex]::Escape($term))( |$)") {
                $score += 20
            }
        }
    }

    $penaltyTerms = @(
        "babyfood", "baby food", "restaurant", "flour", "pasta", "noodle", "noodles",
        "mix", "soup", "broth", "bouillon", "cake", "chips", "snack", "bar",
        "cereal", "pudding", "skin", "wing", "thigh", "drumstick", "sausage"
    )
    foreach ($term in $penaltyTerms) {
        if ($normalizedDescription.Contains($term)) {
            $score -= 35
        }
    }

    if ($normalizedDescription.Contains("without salt") -or $normalizedDescription.Contains("no salt")) {
        $score += 15
    }
    if ($normalizedDescription.Contains("with salt") -or $normalizedDescription.Contains("salt added")) {
        $score -= 10
    }
    if ($DataType -eq "Foundation") {
        $score += 10
    } elseif ($DataType -eq "SR Legacy") {
        $score += 5
    }

    $score -= [Math]::Min(30, [Math]::Floor($normalizedDescription.Length / 20))
    return $score
}

function Test-CoreNutritionComplete {
    param(
        [string] $Calories,
        [string] $Protein,
        [string] $Fat,
        [string] $Carbs
    )

    $values = @($Calories, $Protein, $Fat, $Carbs)
    foreach ($value in $values) {
        if ([string]::IsNullOrWhiteSpace($value)) {
            return $false
        }
    }

    $caloriesValue = [double]::Parse($Calories, [System.Globalization.CultureInfo]::InvariantCulture)
    $proteinValue = [double]::Parse($Protein, [System.Globalization.CultureInfo]::InvariantCulture)
    $fatValue = [double]::Parse($Fat, [System.Globalization.CultureInfo]::InvariantCulture)
    $carbsValue = [double]::Parse($Carbs, [System.Globalization.CultureInfo]::InvariantCulture)

    if ($caloriesValue -lt 0 -or $caloriesValue -gt 1000) { return $false }
    if ($proteinValue -lt 0 -or $fatValue -lt 0 -or $carbsValue -lt 0) { return $false }
    if (($proteinValue + $fatValue + $carbsValue) -gt 120) { return $false }

    return $true
}
if ($RunRuleTests) {
    $cases = @(
        @{ name = "raw rejects cooked"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "chicken breast raw" -Description "Chicken breast, cooked, braised" },
        @{ name = "raw accepts raw"; expected = $true; actual = Test-DescriptionMatchesQuery -Query "banana raw" -Description "Bananas, raw" },
        @{ name = "raw rejects unspecified composite"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "fish tuna fresh raw" -Description "Fish, tuna salad" },
        @{ name = "whole milk rejects cheese"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "milk whole 3.25 milkfat" -Description "Cheese, feta, whole milk, crumbled" },
        @{ name = "whole milk rejects dry powder"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "milk whole 3.25 milkfat" -Description "Milk, dry, whole" },
        @{ name = "whole milk rejects composite beverage"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "milk whole 3.25 milkfat" -Description "Beverages, chocolate syrup, prepared with whole milk" },
        @{ name = "water rejects similarly named vegetable"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "water" -Description "Water convolvulus, cooked" },
        @{ name = "generic cooked chicken rejects fried"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "chicken breast cooked" -Description "Chicken breast, cooked, fried" },
        @{ name = "sunflower oil rejects roasted kernels"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "sunflower oil" -Description "Seeds, sunflower seed kernels, oil roasted" },
        @{ name = "cooked rejects raw"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "rice white cooked" -Description "Rice, white, raw" },
        @{ name = "cooked accepts steamed"; expected = $true; actual = Test-DescriptionMatchesQuery -Query "rice white cooked" -Description "Rice, white, cooked, steamed" },
        @{ name = "boiled rejects baked"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "potato boiled" -Description "Potatoes, baked" },
        @{ name = "boiled accepts boiled"; expected = $true; actual = Test-DescriptionMatchesQuery -Query "potato boiled" -Description "Potatoes, boiled" },
        @{ name = "prepared accepts canned"; expected = $true; actual = Test-DescriptionMatchesQuery -Query "fish sardine canned oil" -Description "Fish, sardine, canned in oil" },
        @{ name = "cooked rejects canned"; expected = $false; actual = Test-DescriptionMatchesQuery -Query "carrot cooked" -Description "Carrot juice, canned" }
    )
    $atwaterFood = [pscustomobject]@{
        foodNutrients = @([pscustomobject]@{
            nutrientId = 2047
            nutrientNumber = "957"
            nutrientName = "Energy (Atwater General Factors)"
            value = 64.7
        })
    }
    $atwaterCalories = Get-NutrientValue -Food $atwaterFood -Numbers @("208", "957", "958") -NutrientIds @("1008", "2047", "2048") -Names @() -DecimalPlaces 0
    if ($atwaterCalories -ne "65") {
        throw "USDA Atwater energy fallback rule failed."
    }

    $failures = @($cases | Where-Object { $_.actual -ne $_.expected })
    if ($failures.Count -gt 0) {
        throw "USDA generic export rule tests failed: $($failures.name -join ', ')"
    }
    [pscustomobject]@{ passed = $cases.Count + 1; failed = 0 } | ConvertTo-Json
    return
}

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    throw "USDA FoodData Central API key is required. Pass -ApiKey or set USDA_FOODDATA_API_KEY."
}
if (-not [string]::IsNullOrWhiteSpace($QueryFile)) {
    if (-not (Test-Path -LiteralPath $QueryFile)) {
        throw "USDA query file was not found: $QueryFile"
    }

    $Queries = Get-Content -LiteralPath $QueryFile |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith("#") } |
        Select-Object -Unique
}
$resolvedOutput = Resolve-OutputFile -Path $OutputPath
$seenFdcIds = [System.Collections.Generic.HashSet[string]]::new()
$rows = [System.Collections.Generic.List[object]]::new()
$queryDiagnostics = [System.Collections.Generic.List[object]]::new()
$encodedDataTypes = [System.Uri]::EscapeDataString(($DataTypes -join ","))
$rowsFilteredByDescription = 0
$rowsFilteredByNutrition = 0
$rowsSeen = 0

foreach ($query in $Queries) {
    $encodedQuery = [System.Uri]::EscapeDataString($query)
    $uri = "$BaseUrl`?api_key=$ApiKey&query=$encodedQuery&dataType=$encodedDataTypes&pageSize=$PageSize&pageNumber=1"
    $result = Invoke-RestMethod -Uri $uri -Method Get

    if ($null -eq $result.foods) {
        $queryDiagnostics.Add([pscustomobject]@{ query = $query; candidates = 0; valid = 0; exported = 0 }) | Out-Null
        continue
    }

    $queryCandidateCount = @($result.foods).Count
    $queryDescriptionMatches = 0
    $queryNutritionMatches = 0
    $queryRows = [System.Collections.Generic.List[object]]::new()
    foreach ($food in $result.foods) {
        $rowsSeen++
        $fdcId = [string] $food.fdcId
        if ([string]::IsNullOrWhiteSpace($fdcId) -or $seenFdcIds.Contains($fdcId)) {
            continue
        }

        $description = Get-CleanDescription -Description ([string] $food.description)
        if ($null -eq $description) {
            continue
        }

        if (-not (Test-DescriptionMatchesQuery -Query $query -Description $description)) {
            $rowsFilteredByDescription++
            continue
        }

        $queryDescriptionMatches++
        $calories = Get-NutrientValue -Food $food -Numbers @("208", "957", "958") -NutrientIds @("1008", "2047", "2048") -Names @() -DecimalPlaces 0
        $protein = Get-NutrientValue -Food $food -Numbers @("203") -NutrientIds @("1003") -Names @("Protein")
        $fat = Get-NutrientValue -Food $food -Numbers @("204") -NutrientIds @("1004") -Names @("Total lipid", "Total fat")
        $carbs = Get-NutrientValue -Food $food -Numbers @("205") -NutrientIds @("1005") -Names @("Carbohydrate")

        if (-not (Test-CoreNutritionComplete -Calories $calories -Protein $protein -Fat $fat -Carbs $carbs)) {
            $rowsFilteredByNutrition++
            continue
        }
        $queryNutritionMatches++
        $row = [pscustomobject]@{
            catalog_type = "GENERIC_INGREDIENT"
            data_source = "USDA_FOODDATA"
            fdc_id = $fdcId
            source_key = "USDA_FOODDATA:fdc:$fdcId"
            name = $description
            calories = $calories
            protein = $protein
            fat = $fat
            carbs = $carbs
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
            preparation_state = $(
                $resolvedPreparationState = Get-PreparationState -Text $description
                if ($resolvedPreparationState -eq "UNSPECIFIED") {
                    Get-PreparationState -Text $query
                } else {
                    $resolvedPreparationState
                }
            )
            alias_en = $query
            source_note = "USDA FoodData Central; dataType=$($food.dataType); query=$query"
            relevance_score = Get-DescriptionRelevanceScore -Query $query -Description $description -DataType ([string] $food.dataType)
        }
        $queryRows.Add($row) | Out-Null
    }

    $selectedRows = @(
        $queryRows |
            Sort-Object -Property @{ Expression = "relevance_score"; Descending = $true }, @{ Expression = "name"; Ascending = $true } |
            Select-Object -First $MaxRowsPerQuery
    )
    $exportedForQuery = 0
    foreach ($selectedRow in $selectedRows) {
        if ($seenFdcIds.Add([string] $selectedRow.fdc_id)) {
            $rows.Add($selectedRow) | Out-Null
            $exportedForQuery++
        }
    }
    $queryDiagnostics.Add([pscustomobject]@{
        query = $query
        candidates = $queryCandidateCount
        descriptionMatches = $queryDescriptionMatches
        nutritionMatches = $queryNutritionMatches
        valid = $queryRows.Count
        exported = $exportedForQuery
    }) | Out-Null

    if ($RequestDelayMs -gt 0) {
        Start-Sleep -Milliseconds $RequestDelayMs
    }
}

if ($rows.Count -eq 0) {
    throw "No USDA FoodData rows were exported. rowsSeen=$rowsSeen rowsFilteredByDescription=$rowsFilteredByDescription. Check API key, queries, and data types."
}

$csvLines = $rows | Select-Object * -ExcludeProperty relevance_score | ConvertTo-Csv -NoTypeInformation
$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($resolvedOutput, $csvLines, $utf8WithoutBom)

[pscustomobject]@{
    outputPath = $resolvedOutput
    rowsWritten = $rows.Count
    rowsFilteredByDescription = $rowsFilteredByDescription
    rowsFilteredByNutrition = $rowsFilteredByNutrition
    rowsSeen = $rowsSeen
    queriesWithRows = @($queryDiagnostics | Where-Object { $_.exported -gt 0 }).Count
    queriesWithoutRows = @($queryDiagnostics | Where-Object { $_.exported -eq 0 }).Count
    missingQueries = @($queryDiagnostics | Where-Object { $_.exported -eq 0 } | ForEach-Object { $_.query })
    queryDiagnostics = $queryDiagnostics
    queries = $Queries
    marketRegion = $MarketRegion
    dataTypes = $DataTypes
    pageSize = $PageSize
    maxRowsPerQuery = $MaxRowsPerQuery
} | ConvertTo-Json -Depth 5
