param(
    [string] $InputPath,

    [string] $DownloadUrl = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz",

    [string] $CachePath = ".\outputs\openfoodfacts-products.csv.gz",

    [switch] $DownloadIfMissing,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [ValidateSet("UK_IE", "TR", "EU", "GLOBAL")]
    [string] $MarketRegion = "UK_IE",

    [string[]] $StoreTerms = @(),

    [string[]] $CountryTerms = @(),

    [ValidateRange(1, 1000000)]
    [int] $Limit = 10000,

    [ValidateRange(1, 100000000)]
    [int] $MaxRowsToRead = 1000000,

    [switch] $RequireCalories,

    [switch] $RequireMacroData
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName Microsoft.VisualBasic

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

function Resolve-InputFile {
    if (-not [string]::IsNullOrWhiteSpace($InputPath)) {
        if (-not (Test-Path -LiteralPath $InputPath -PathType Leaf)) {
            throw "Input file was not found: $InputPath"
        }
        return (Resolve-Path -LiteralPath $InputPath).Path
    }

    if (Test-Path -LiteralPath $CachePath -PathType Leaf) {
        return (Resolve-Path -LiteralPath $CachePath).Path
    }

    if (-not $DownloadIfMissing) {
        throw "Bulk input file is required. Provide -InputPath or use -DownloadIfMissing to download $DownloadUrl to $CachePath."
    }

    $resolvedCachePath = Resolve-OutputFile -Path $CachePath
    Write-Host "Downloading Open Food Facts bulk export to $resolvedCachePath"
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $resolvedCachePath
    return $resolvedCachePath
}

function Get-RegionCountryTerms {
    param([string] $Region)

    if ($CountryTerms.Count -gt 0) {
        return $CountryTerms
    }

    switch ($Region) {
        "UK_IE" { return @("united kingdom", "en:united-kingdom", "ireland", "en:ireland", "gb", "uk") }
        "TR" { return @("turkey", "turkiye", "tÃ¼rkiye", "en:turkey", "tr") }
        "EU" { return @("european union", "europe", "france", "germany", "spain", "italy", "netherlands", "belgium", "poland", "ireland", "united kingdom") }
        default { return @() }
    }
}

function Normalize-Terms {
    param([string[]] $Terms)

    return @($Terms |
        ForEach-Object { $_ -split "," } |
        ForEach-Object { $_.Trim().ToLowerInvariant() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
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
        if ($normalizedValue.Contains($term)) {
            return $true
        }
    }
    return $false
}

function Get-Field {
    param(
        [string] $Names
    )

    foreach ($name in ($Names -split "\|")) {
        $normalizedName = Normalize-Header -Value $name
        if (-not $script:HeaderIndex.ContainsKey($normalizedName)) {
            continue
        }
        $index = $script:HeaderIndex[$normalizedName]
        if ($index -lt $script:CurrentRowValues.Count) {
            $value = Normalize-Value -Value $script:CurrentRowValues[$index]
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                return $value
            }
        }
    }
    return $null
}

function Normalize-Header {
    param([string] $Value)

    if ($null -eq $Value) {
        return $null
    }

    return $Value.Trim().Trim([char]0xFEFF).Trim('"')
}

function Normalize-Value {
    param([string] $Value)

    if ($null -eq $Value) {
        return $null
    }

    return $Value.Trim().Trim('"')
}

function Test-Decimal {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    $parsed = 0.0
    return [double]::TryParse(
        $Value.Replace(",", "."),
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref] $parsed
    )
}

function Escape-Tsv {
    param([string] $Value)

    if ($null -eq $Value) {
        return ""
    }

    $escaped = $Value.Replace('"', '""')
    if ($escaped.Contains("`t") -or $escaped.Contains("`n") -or $escaped.Contains("`r") -or $escaped.Contains('"')) {
        return '"' + $escaped + '"'
    }
    return $escaped
}

function New-Reader {
    param([string] $Path)

    $stream = [System.IO.File]::OpenRead($Path)
    if ($Path.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)) {
        $gzip = [System.IO.Compression.GZipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
        return [pscustomobject] @{
            Stream = $stream
            Gzip = $gzip
            Reader = [System.IO.StreamReader]::new($gzip, [System.Text.Encoding]::UTF8)
        }
    }

    return [pscustomobject] @{
        Stream = $stream
        Gzip = $null
        Reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
    }
}

$resolvedInput = Resolve-InputFile
$resolvedOutput = Resolve-OutputFile -Path $OutputPath
$countryTermsNormalized = Normalize-Terms -Terms (Get-RegionCountryTerms -Region $MarketRegion)
$storeTermsNormalized = Normalize-Terms -Terms $StoreTerms
$seenBarcodes = [System.Collections.Generic.HashSet[string]]::new()
$rowsRead = 0
$rowsWritten = 0
$rowsSkipped = 0
$countryFilteredRows = 0
$storeFilteredRows = 0
$duplicateRows = 0
$missingNutritionRows = 0
$malformedRows = 0
$outputColumns = @(
    "code",
    "product_name",
    "brands",
    "countries_tags",
    "countries_tags_en",
    "stores",
    "stores_tags",
    "serving_size",
    "serving_quantity",
    "serving_quantity_unit",
    "energy-kcal_100g",
    "proteins_100g",
    "fat_100g",
    "carbohydrates_100g",
    "fiber_100g",
    "sugars_100g",
    "sodium_100g",
    "allergens_tags",
    "nutrition_grade_fr",
    "nutriscore_grade",
    "image_url",
    "image_front_url"
)

$readerState = New-Reader -Path $resolvedInput
$writer = [System.IO.StreamWriter]::new($resolvedOutput, $false, [System.Text.Encoding]::UTF8)
$script:CurrentRowValues = @()
$script:HeaderIndex = @{}
try {
    $parser = [Microsoft.VisualBasic.FileIO.TextFieldParser]::new($readerState.Reader)
    $parser.SetDelimiters("`t")
    $parser.HasFieldsEnclosedInQuotes = $true

    $headers = $parser.ReadFields()
    if ($null -eq $headers -or $headers.Count -eq 0) {
        throw "Bulk export header row could not be read."
    }

    $headerIndex = @{}
    for ($i = 0; $i -lt $headers.Count; $i++) {
        $headerName = Normalize-Header -Value $headers[$i]
        if (-not [string]::IsNullOrWhiteSpace($headerName)) {
            $headerIndex[$headerName] = $i
        }
    }

    $writer.WriteLine(($outputColumns | ForEach-Object { Escape-Tsv $_ }) -join "`t")

    while (-not $parser.EndOfData -and $rowsWritten -lt $Limit) {
        try {
            $values = $parser.ReadFields()
        } catch {
            $rowsRead++
            $rowsSkipped++
            $malformedRows++
            continue
        }
        $script:CurrentRowValues = $values
        $rowsRead++
        if ($rowsRead -gt $MaxRowsToRead) {
            break
        }

        $script:HeaderIndex = $headerIndex
        $barcode = Get-Field -Names "code|barcode"
        $name = Get-Field -Names "product_name|product_name_en|generic_name"
        if ([string]::IsNullOrWhiteSpace($barcode) -or $barcode -notmatch "^\d{8,14}$" -or [string]::IsNullOrWhiteSpace($name)) {
            $rowsSkipped++
            continue
        }
        if (-not $seenBarcodes.Add($barcode)) {
            $duplicateRows++
            continue
        }

        $countryParts = @()
        $countryParts += Get-Field -Names "countries_tags"
        $countryParts += Get-Field -Names "countries_tags_en"
        $countryParts += Get-Field -Names "countries"
        $countryValue = $countryParts -join " "
        if (-not (Test-AnyTermMatch -Value $countryValue -Terms $countryTermsNormalized)) {
            $countryFilteredRows++
            continue
        }

        $storeParts = @()
        $storeParts += Get-Field -Names "stores"
        $storeParts += Get-Field -Names "stores_tags"
        $storeValue = $storeParts -join " "
        if (-not (Test-AnyTermMatch -Value $storeValue -Terms $storeTermsNormalized)) {
            $storeFilteredRows++
            continue
        }

        $calories = Get-Field -Names "energy-kcal_100g|energy_kcal_100g"
        $protein = Get-Field -Names "proteins_100g|protein_100g"
        $fat = Get-Field -Names "fat_100g"
        $carbs = Get-Field -Names "carbohydrates_100g|carbs_100g"
        if ($RequireCalories -and -not (Test-Decimal -Value $calories)) {
            $missingNutritionRows++
            continue
        }
        if ($RequireMacroData -and -not ((Test-Decimal -Value $protein) -or (Test-Decimal -Value $fat) -or (Test-Decimal -Value $carbs))) {
            $missingNutritionRows++
            continue
        }

        $outputValues = foreach ($column in $outputColumns) {
            Get-Field -Names $column
        }
        $writer.WriteLine(($outputValues | ForEach-Object { Escape-Tsv $_ }) -join "`t")
        $rowsWritten++
    }
} finally {
    if ($parser) {
        $parser.Close()
    }
    $writer.Dispose()
    $readerState.Reader.Dispose()
    if ($readerState.Gzip) {
        $readerState.Gzip.Dispose()
    }
    $readerState.Stream.Dispose()
}

[pscustomobject] @{
    inputPath = $resolvedInput
    outputPath = $resolvedOutput
    marketRegion = $MarketRegion
    storeTerms = $storeTermsNormalized
    rowsRead = $rowsRead
    rowsWritten = $rowsWritten
    rowsSkipped = $rowsSkipped
    duplicateRows = $duplicateRows
    malformedRows = $malformedRows
    countryFilteredRows = $countryFilteredRows
    storeFilteredRows = $storeFilteredRows
    missingNutritionRows = $missingNutritionRows
    limit = $Limit
    maxRowsToRead = $MaxRowsToRead
    requireCalories = $RequireCalories.IsPresent
    requireMacroData = $RequireMacroData.IsPresent
} | ConvertTo-Json -Depth 4



