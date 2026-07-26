param(
    [switch]$IncludePostgresBenchmark,
    [switch]$IncludeSearchScaleGate
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$productTests = @(
    "AdminFoodProductReviewControllerTest",
    "FoodItemControllerTest",
    "FoodItemServiceImplTest",
    "FoodItemServiceSearchIntegrationTest",
    "GoldenFoodSearchQualityGateTest",
    "GenericFoodManifestGateTest",
    "FoodProductImportServiceImplTest",
    "FoodProductImportPerformanceIntegrationTest",
    "FoodProductImportPostgresPerformanceIntegrationTest",
    "FoodProductEvidenceServiceImplTest",
    "FoodProductContributionServiceImplTest",
    "FoodProductContributionControllerTest",
    "FoodProductQualityIssueTrackerTest",
    "FoodProductReviewServiceImplTest",
    "OpenFoodFactsServiceImplTest",
    "ProductQualitySuggestionServiceImplTest",
    "ProductQualitySuggestionReconciliationServiceTest",
    "FoodProductNormalizationRulesTest",
    "NutritionValueNormalizerTest",
    "BatchQuerySupportTest"
) -join ","

Push-Location $projectRoot
try {
    & powershell -ExecutionPolicy Bypass -File .\scripts\test-tr-food-source-registry.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "TR food source registry contract failed."
    }

    & powershell -ExecutionPolicy Bypass -File .\scripts\test-tr-internet-capacity-contract.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "TR internet capacity contract failed."
    }
    & powershell -ExecutionPolicy Bypass -File .\scripts\test-tr-market-evidence-assessment.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "TR market evidence assessment contract failed."
    }

    & powershell -ExecutionPolicy Bypass -File .\scripts\generate-generic-food-manifest-queries.ps1 -Check
    if ($LASTEXITCODE -ne 0) {
        throw "Generic food manifest query contract failed."
    }

    & powershell -ExecutionPolicy Bypass -File .\scripts\generate-generic-food-approved-seed.ps1 -Check
    if ($LASTEXITCODE -ne 0) {
        throw "Generic food approved seed contract failed."
    }

    & powershell -ExecutionPolicy Bypass -File .\scripts\export-usda-fooddata-generic-products.ps1 -RunRuleTests
    if ($LASTEXITCODE -ne 0) {
        throw "USDA generic export preparation-state rules failed."
    }

    & powershell -ExecutionPolicy Bypass -File .\scripts\test-open-food-facts-market-batch.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "Open Food Facts market batch contract failed."
    }

    & .\mvnw.cmd "-Dtest=$productTests" "-Dspring.jpa.show-sql=false" "-Dspring.jpa.properties.hibernate.show_sql=false" test
    if ($LASTEXITCODE -ne 0) {
        throw "Product-management backend regression failed."
    }

    & npm --prefix admin-ui run test:quality-workbench
    if ($LASTEXITCODE -ne 0) {
        throw "Admin product quality workbench UI contract failed."
    }

    & npm --prefix admin-ui run build
    if ($LASTEXITCODE -ne 0) {
        throw "Admin UI production build failed."
    }

    if ($IncludePostgresBenchmark) {
        & powershell -ExecutionPolicy Bypass -File .\scripts\test-product-flyway-postgres.ps1
        if ($LASTEXITCODE -ne 0) {
            throw "Product Flyway PostgreSQL validation failed."
        }

        & powershell -ExecutionPolicy Bypass -File .\scripts\test-food-import-postgres-performance.ps1
        if ($LASTEXITCODE -ne 0) {
            throw "Product-management PostgreSQL benchmark failed."
        }
    }

    if ($IncludeSearchScaleGate) {
        & powershell -ExecutionPolicy Bypass -File .\scripts\test-food-search-postgres-scale.ps1
        if ($LASTEXITCODE -ne 0) {
            throw "Product search PostgreSQL scale gate failed."
        }
    }

    Write-Output "PRODUCT_MANAGEMENT_REGRESSION_PASSED"
}
finally {
    Pop-Location
}
