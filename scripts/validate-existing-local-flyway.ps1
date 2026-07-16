param(
    [string]$EnvironmentFile = ".env"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $projectRoot $EnvironmentFile

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Environment file was not found: $Path"
    }

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim().Trim('"'), "Process")
        }
    }
}

Push-Location $projectRoot
try {
    Import-DotEnv -Path $environmentPath

    if (-not $env:SPRING_DATASOURCE_USERNAME) {
        $env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
    }
    if (-not $env:SPRING_DATASOURCE_PASSWORD) {
        $env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
    }

    $env:GRUN_RUN_EXISTING_LOCAL_FLYWAY_VALIDATE = "true"
    & .\mvnw.cmd "-Dtest=ExistingLocalFlywayChecksumValidationTest" test
    if ($LASTEXITCODE -ne 0) {
        throw "Existing local Flyway checksum validation failed. Do not run Flyway repair automatically."
    }

    Write-Output "EXISTING_LOCAL_FLYWAY_CHECKSUMS_VALID"
}
finally {
    Remove-Item Env:GRUN_RUN_EXISTING_LOCAL_FLYWAY_VALIDATE -ErrorAction SilentlyContinue
    Pop-Location
}
