param(
    [switch]$IncludePostgresBenchmark
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$productTests = @(
    "AdminFoodProductReviewControllerTest",
    "FoodItemControllerTest",
    "FoodItemServiceImplTest",
    "FoodItemServiceSearchIntegrationTest",
    "FoodProductImportServiceImplTest",
    "FoodProductImportPerformanceIntegrationTest",
    "FoodProductImportPostgresPerformanceIntegrationTest",
    "FoodProductEvidenceServiceImplTest",
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
    & .\mvnw.cmd "-Dtest=$productTests" test
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

    Write-Output "PRODUCT_MANAGEMENT_REGRESSION_PASSED"
}
finally {
    Pop-Location
}
