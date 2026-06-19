param(
    [Parameter(Mandatory = $true)]
    [string] $OutputPath,

    [ValidateSet("UK_IE", "TR", "EU", "GLOBAL")]
    [string] $MarketRegion = "UK_IE",

    [string[]] $Stores = @("tesco", "dunnes"),

    [ValidateRange(1, 10000)]
    [int] $Limit = 100,

    [ValidateRange(1, 100)]
    [int] $PageSize = 50,

    [switch] $DisableCountryFallback,

    [ValidateRange(0, 10000)]
    [int] $RequestDelayMs = 250,

    [string] $BaseUrl = "https://world.openfoodfacts.org",

    [string] $UserAgent = "GRunCalorieTracker/0.1 (contact: local-dev@grun.app)"
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

function Get-CountryQuery {
    param([string] $Region)

    switch ($Region) {
        "UK_IE" { return @("United Kingdom", "Ireland") }
        "TR" { return @("Turkey") }
        "EU" { return @("France", "Germany", "Spain", "Italy", "Netherlands", "Belgium", "Poland", "Ireland") }
        default { return @() }
    }
}

function Get-FirstText {
    param($Value)

    if ($null -eq $Value) {
        return ""
    }

    if ($Value -is [array]) {
        return ($Value -join ", ")
    }

    return [string] $Value
}

function Get-Nutriment {
    param($Product, [string] $Name)

    if ($null -eq $Product.nutriments) {
        return ""
    }

    $property = $Product.nutriments.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return ""
    }

    return [string] $property.Value
}

function Add-ProductsFromSearch {
    param(
        [string] $Country,
        [string] $Store,
        [string] $SearchMode
    )

    $page = 1
    while ($rows.Count -lt $Limit) {
        $encodedCountry = [System.Uri]::EscapeDataString($Country)
        $storeQuery = ""
        if (-not [string]::IsNullOrWhiteSpace($Store)) {
            $encodedStore = [System.Uri]::EscapeDataString($Store)
            $storeQuery = "&stores_tags=$encodedStore"
        }

        $uri = "$BaseUrl/api/v2/search?countries_tags_en=$encodedCountry$storeQuery&fields=$fields&page_size=$PageSize&page=$page&sort_by=unique_scans_n"
        try {
            $result = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
        } catch {
            Write-Warning "Open Food Facts request failed for mode '$SearchMode', store '$Store', country '$Country', page '$page'. $($_.Exception.Message)"
            break
        }

        if ($null -eq $result.products -or $result.products.Count -eq 0) {
            break
        }

        foreach ($product in $result.products) {
            if ($rows.Count -ge $Limit) {
                break
            }

            $barcode = [string] $product.code
            if ([string]::IsNullOrWhiteSpace($barcode) -or -not $seen.Add($barcode)) {
                continue
            }

            $rows.Add([pscustomobject] @{
                code = $barcode
                product_name = Get-FirstText $product.product_name
                brands = Get-FirstText $product.brands
                countries_tags = Get-FirstText $product.countries_tags
                countries_tags_en = Get-FirstText $product.countries_tags_en
                stores_tags = Get-FirstText $product.stores_tags
                serving_size = Get-FirstText $product.serving_size
                "energy-kcal_100g" = Get-Nutriment -Product $product -Name "energy-kcal_100g"
                "proteins_100g" = Get-Nutriment -Product $product -Name "proteins_100g"
                "fat_100g" = Get-Nutriment -Product $product -Name "fat_100g"
                "carbohydrates_100g" = Get-Nutriment -Product $product -Name "carbohydrates_100g"
                "fiber_100g" = Get-Nutriment -Product $product -Name "fiber_100g"
                "sugars_100g" = Get-Nutriment -Product $product -Name "sugars_100g"
                "salt_100g" = Get-Nutriment -Product $product -Name "salt_100g"
                "sodium_100g" = Get-Nutriment -Product $product -Name "sodium_100g"
                allergens_tags = Get-FirstText $product.allergens_tags
                nutriscore_grade = Get-FirstText $product.nutriscore_grade
                image_front_url = Get-FirstText $product.image_front_url
                image_url = Get-FirstText $product.image_url
                export_search_mode = $SearchMode
                export_store_filter = $Store
            })
        }

        if ($result.products.Count -lt $PageSize) {
            break
        }

        if ($RequestDelayMs -gt 0) {
            Start-Sleep -Milliseconds $RequestDelayMs
        }
        $page++
    }
}

$resolvedOutput = Resolve-OutputFile -Path $OutputPath
$countries = Get-CountryQuery -Region $MarketRegion
$normalizedStores = $Stores |
    ForEach-Object { $_ -split "," } |
    ForEach-Object { $_.Trim() } |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$headers = @{ "User-Agent" = $UserAgent }
$fields = "code,product_name,brands,nutriments,countries_tags,countries_tags_en,stores_tags,allergens_tags,nutriscore_grade,serving_size,image_front_url,image_url"
$rows = [System.Collections.Generic.List[object]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new()

foreach ($store in $normalizedStores) {
    foreach ($country in $countries) {
        Add-ProductsFromSearch -Country $country -Store $store -SearchMode "STORE_PRIORITY"
    }
}

if (-not $DisableCountryFallback -and $rows.Count -lt $Limit) {
    foreach ($country in $countries) {
        Add-ProductsFromSearch -Country $country -Store $null -SearchMode "COUNTRY_FALLBACK"
    }
}

if ($rows.Count -eq 0) {
    throw "No Open Food Facts products were exported for region '$MarketRegion' and stores '$($normalizedStores -join ", ")'."
}

$rows | Export-Csv -Path $resolvedOutput -NoTypeInformation -Delimiter "`t" -Encoding UTF8

[pscustomobject] @{
    outputPath = $resolvedOutput
    marketRegion = $MarketRegion
    baseUrl = $BaseUrl
    stores = $normalizedStores
    countryFallbackEnabled = -not $DisableCountryFallback.IsPresent
    rowsWritten = $rows.Count
} | ConvertTo-Json -Depth 4
