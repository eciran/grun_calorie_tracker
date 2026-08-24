param(
    [string]$MobileRoot = "C:\Users\emrah\IdeaProjects\Claude_Grun_frontend"
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $PSScriptRoot

Push-Location $backendRoot
try {
    & .\mvnw.cmd "-Dtest=TestFeedbackPropertiesTest,TestFeedbackMigrationContractTest,TestFeedbackSubmissionServiceImplTest,AdminTestFeedbackServiceImplTest,TestFeedbackScreenshotServiceImplTest" test
    if ($LASTEXITCODE -ne 0) { throw "Backend test-feedback gate failed." }

    Push-Location (Join-Path $backendRoot "admin-ui")
    try {
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "Admin UI build gate failed." }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}

Push-Location $MobileRoot
try {
    npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw "Mobile typecheck gate failed." }
    node qa/test-feedback-contract.mjs
    if ($LASTEXITCODE -ne 0) { throw "Mobile test-feedback contract failed." }
} finally {
    Pop-Location
}

Write-Output "TEST_FEEDBACK_RELEASE_GATE_PASS"
Write-Output "Device evidence remains required for Android and iOS preview builds."
