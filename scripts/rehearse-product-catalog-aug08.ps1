param(
    [string]$BundleDirectory = ".\outputs\product-catalog-aug08\licensed-default-20260802",
    [int]$DatabasePort = 55437,
    [int]$ApplicationPort = 18082,
    [string]$PostgresImage = "postgres:16",
    [switch]$ReuseExistingJar
)

$ErrorActionPreference = "Stop"
$containerName = "grun-product-catalog-aug08-postgres"
Add-Type -AssemblyName System.Net.Http
$databaseName = "grun_catalog_aug08"
$databaseUser = "catalog_aug08"
$databasePassword = "catalog-aug08-local-only"
$adminEmail = "catalog-aug08-admin@grun.local"
$adminPassword = "Catalog-Aug08-Local-Pass1!"
$projectRoot = Split-Path -Parent $PSScriptRoot
$bundleRoot = if ([IO.Path]::IsPathRooted($BundleDirectory)) { $BundleDirectory } else { Join-Path $projectRoot $BundleDirectory }
$manifestPath = Join-Path $bundleRoot "bundle-manifest.json"
$evidenceRoot = Join-Path $bundleRoot "rehearsal"
$appLogPath = Join-Path $evidenceRoot "application.log"
$appErrorPath = Join-Path $evidenceRoot "application-error.log"
$reportPath = Join-Path $evidenceRoot "rehearsal-report.json"
$appProcess = $null
$token = $null

function Invoke-DockerChecked {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: docker $($Arguments -join ' ')" }
}

function Invoke-Psql {
    param([string]$Database, [string]$Sql)
    $value = & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $Database -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed." }
    return ($value | Select-Object -Last 1).Trim()
}

function Test-PortFree {
    param([int]$Port)
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "Port $Port is already in use." } finally { $listener.Stop() }
}

function Write-JsonFile {
    param([string]$Path, $Value)
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 20) + "`n"), [Text.UTF8Encoding]::new($false))
}

function Invoke-ChunkImport {
    param($Chunk, [string]$PassName)
    $chunkPath = Join-Path $bundleRoot $Chunk.file
    if (-not (Test-Path -LiteralPath $chunkPath -PathType Leaf)) { throw "Missing chunk: $($Chunk.file)" }
    $actualHash = (Get-FileHash -LiteralPath $chunkPath -Algorithm SHA256).Hash
    if ($actualHash -ne $Chunk.sha256) { throw "Chunk hash differs: $($Chunk.file)" }
    if ((Get-Item -LiteralPath $chunkPath).Length -gt 5767168) { throw "Chunk exceeds the safe multipart size: $($Chunk.file)" }

    $uri = "http://127.0.0.1:$ApplicationPort/api/v1/admin/products/import?importMode=RAW_EXTERNAL&importFormat=GRUN_STANDARD"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $client = [Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromMinutes(15)
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $script:token)
    $multipart = [Net.Http.MultipartFormDataContent]::new()
    $fileStream = [IO.File]::OpenRead($chunkPath)
    $fileContent = [Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new("text/csv")
    $multipart.Add($fileContent, "file", [IO.Path]::GetFileName($chunkPath))
    try {
        $response = $client.PostAsync($uri, $multipart).GetAwaiter().GetResult()
        $json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) { throw "Import failed for $($Chunk.file): HTTP $([int]$response.StatusCode). Response: $json" }
    } finally {
        $fileContent.Dispose()
        $fileStream.Dispose()
        $multipart.Dispose()
        $client.Dispose()
        $watch.Stop()
    }
    $result = $json | ConvertFrom-Json
    if ([int]$result.totalRows -ne [int]$Chunk.rows) { throw "Input row count differs for $($Chunk.file)." }
    if ([int]$result.savedRows -ne [int]$Chunk.rows -or [int]$result.skippedRows -ne 0 -or [int]$result.duplicateInputRows -ne 0) {
        throw "Import gate failed for $($Chunk.file): saved=$($result.savedRows), skipped=$($result.skippedRows), duplicates=$($result.duplicateInputRows)."
    }
    $safeName = ($Chunk.file -replace '^import-chunks/', '' -replace '\.csv$', '')
    Write-JsonFile -Path (Join-Path $evidenceRoot "$safeName.$PassName.result.json") -Value $result
    return [pscustomobject]@{
        file = $Chunk.file
        rows = [int]$Chunk.rows
        inserted = [int]$result.insertedRows
        updated = [int]$result.updatedRows
        saved = [int]$result.savedRows
        skipped = [int]$result.skippedRows
        elapsedMs = $watch.ElapsedMilliseconds
    }
}

function Invoke-Pass {
    param([object[]]$Chunks, [string]$Name)
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $results = foreach ($chunk in $Chunks) { Invoke-ChunkImport -Chunk $chunk -PassName $Name }
    $watch.Stop()
    return [pscustomobject]@{
        name = $Name
        elapsedMs = $watch.ElapsedMilliseconds
        rows = [int](($results.rows | Measure-Object -Sum).Sum)
        inserted = [int](($results.inserted | Measure-Object -Sum).Sum)
        updated = [int](($results.updated | Measure-Object -Sum).Sum)
        saved = [int](($results.saved | Measure-Object -Sum).Sum)
        skipped = [int](($results.skipped | Measure-Object -Sum).Sum)
        chunks = @($results)
    }
}

Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Bundle manifest not found: $manifestPath" }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash
    $chunks = @($manifest.artifacts | ForEach-Object { $_.chunks })
    if ($chunks.Count -eq 0) { throw "Bundle has no chunks." }
    $expectedRows = [int]$manifest.counts.inputRows
    $expectedCanonical = [int]$manifest.counts.expectedCanonicalProducts
    $expectedMerges = [int]$manifest.counts.expectedCrossMarketMerges
    if ([int](($chunks.rows | Measure-Object -Sum).Sum) -ne $expectedRows) { throw "Manifest chunk rows differ from input rows." }

    Test-PortFree $DatabasePort
    Test-PortFree $ApplicationPort
    $existing = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}"
    if ($LASTEXITCODE -ne 0) { throw "Docker is not available." }
    if ($existing) { throw "Container '$containerName' already exists." }

    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    Invoke-DockerChecked -Arguments @(
        "run", "--rm", "-d", "--name", $containerName,
        "-e", "POSTGRES_USER=$databaseUser",
        "-e", "POSTGRES_PASSWORD=$databasePassword",
        "-e", "POSTGRES_DB=$databaseName",
        "-p", "127.0.0.1:${DatabasePort}:5432", $PostgresImage
    ) | Out-Null

    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $databaseReady) { throw "Temporary PostgreSQL did not become ready." }

    $mavenWrapper = Join-Path $projectRoot "mvnw.cmd"
    if (-not $ReuseExistingJar) {
        & $mavenWrapper "-Dmaven.test.skip=true" package
        if ($LASTEXITCODE -ne 0) { throw "Application package build failed." }
    }
    $jar = Get-ChildItem "target/*.jar" | Where-Object { $_.Name -notlike "*.original" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "Application jar was not found." }

    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$DatabasePort/$databaseName"
    $env:SPRING_DATASOURCE_USERNAME = $databaseUser
    $env:SPRING_DATASOURCE_PASSWORD = $databasePassword
    $env:SPRING_PROFILES_ACTIVE = "local"
    $env:SPRING_CACHE_TYPE = "none"
    $env:GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED = "true"
    $env:GRUN_LOCAL_ADMIN_EMAIL = $adminEmail
    $env:GRUN_LOCAL_ADMIN_PASSWORD = $adminPassword
    $env:GRUN_RATE_LIMIT_ENABLED = "false"
    $env:GRUN_RATE_LIMIT_REDIS_ENABLED = "false"
    $env:SERVER_PORT = "$ApplicationPort"
    $env:SPRING_JPA_SHOW_SQL = "false"

    [IO.File]::WriteAllText($appLogPath, "")
    [IO.File]::WriteAllText($appErrorPath, "")
    $javaHomeLine = (& cmd /c "java -XshowSettings:properties -version 2>&1") | Where-Object { $_ -match "^\s*java\.home\s*=" } | Select-Object -First 1
    if (-not $javaHomeLine) { throw "Could not resolve java.home." }
    $javaExecutable = Join-Path (($javaHomeLine -split "=", 2)[1].Trim()) "bin/java.exe"
    $appProcess = Start-Process -FilePath $javaExecutable -ArgumentList @("-jar", $jar.FullName, "--server.port=$ApplicationPort") -RedirectStandardOutput $appLogPath -RedirectStandardError $appErrorPath -WindowStyle Hidden -PassThru

    $applicationReady = $false
    for ($attempt = 0; $attempt -lt 150; $attempt++) {
        if ($appProcess.HasExited) { throw "Rehearsal application exited early. See $appLogPath" }
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$ApplicationPort/actuator/health" -TimeoutSec 2 | Out-Null
            $applicationReady = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $applicationReady) { throw "Rehearsal application did not become ready." }
    Invoke-Psql -Database $databaseName -Sql "update users set role='ADMIN_CATALOG' where email='$adminEmail';" | Out-Null
    $catalogRole = Invoke-Psql -Database $databaseName -Sql "select role from users where email='$adminEmail';"
    if ($catalogRole -ne "ADMIN_CATALOG") { throw "Temporary rehearsal admin could not be granted ADMIN_CATALOG." }


    $loginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:$ApplicationPort/api/v1/auth/admin/login" -Method Post -ContentType "application/json" -Body $loginBody
    $script:token = $login.token
    if (-not $script:token) { throw "Rehearsal admin login did not return a token." }

    Invoke-DockerChecked -Arguments @("exec", $containerName, "pg_dump", "-U", $databaseUser, "-d", $databaseName, "-Fc", "-f", "/tmp/pre-import.dump") | Out-Null

    $firstPass = Invoke-Pass -Chunks $chunks -Name "first"
    if ($firstPass.rows -ne $expectedRows -or $firstPass.inserted -ne $expectedCanonical -or $firstPass.updated -ne $expectedMerges -or $firstPass.skipped -ne 0) {
        throw "First-pass totals differ: rows=$($firstPass.rows), inserted=$($firstPass.inserted), updated=$($firstPass.updated), skipped=$($firstPass.skipped)."
    }
    $firstDatabaseCount = [int](Invoke-Psql -Database $databaseName -Sql "select count(*) from food_items;")
    $firstAvailabilityCount = [int](Invoke-Psql -Database $databaseName -Sql "select count(*) from food_item_market_regions;")
    if ($firstDatabaseCount -ne $expectedCanonical -or $firstAvailabilityCount -ne $expectedRows) {
        throw "First-pass database count differs: products=$firstDatabaseCount, availability=$firstAvailabilityCount."
    }

    $searchResults = @()
    foreach ($market in @("UK_IE", "EU", "TR")) {
        $artifact = $manifest.artifacts | Where-Object { $_.market -eq $market } | Select-Object -First 1
        $sample = Import-Csv -LiteralPath (Join-Path $bundleRoot $artifact.chunks[0].file) | Select-Object -First 1
        $encoded = [Uri]::EscapeDataString($sample.barcode)
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$ApplicationPort/api/v1/products/search?q=$encoded&region=$market&language=EN&page=0&size=5" -Headers @{ Authorization = "Bearer $script:token" }
        $matched = @($response.content | Where-Object { $_.barcode -eq $sample.barcode }).Count -gt 0
        if (-not $matched) { throw "Search smoke failed for $market barcode $($sample.barcode)." }
        $searchResults += [pscustomobject]@{ market = $market; barcode = $sample.barcode; matched = $matched; totalElements = $response.totalElements }
    }

    $secondPass = Invoke-Pass -Chunks $chunks -Name "second"
    if ($secondPass.rows -ne $expectedRows -or $secondPass.inserted -ne 0 -or $secondPass.updated -ne $expectedRows -or $secondPass.skipped -ne 0) {
        throw "Second-pass idempotency differs: rows=$($secondPass.rows), inserted=$($secondPass.inserted), updated=$($secondPass.updated), skipped=$($secondPass.skipped)."
    }
    $secondDatabaseCount = [int](Invoke-Psql -Database $databaseName -Sql "select count(*) from food_items;")
    $secondAvailabilityCount = [int](Invoke-Psql -Database $databaseName -Sql "select count(*) from food_item_market_regions;")
    if ($secondDatabaseCount -ne $firstDatabaseCount -or $secondAvailabilityCount -ne $firstAvailabilityCount) {
        throw "Second pass changed canonical or availability counts."
    }

    Invoke-DockerChecked -Arguments @("exec", $containerName, "createdb", "-U", $databaseUser, "grun_catalog_aug08_rollback") | Out-Null
    Invoke-DockerChecked -Arguments @("exec", $containerName, "pg_restore", "-U", $databaseUser, "-d", "grun_catalog_aug08_rollback", "/tmp/pre-import.dump") | Out-Null
    $rollbackCount = [int](Invoke-Psql -Database "grun_catalog_aug08_rollback" -Sql "select count(*) from food_items;")
    if ($rollbackCount -ne 0) { throw "Rollback snapshot unexpectedly contains $rollbackCount food items." }

    $report = [ordered]@{
        schemaVersion = 1
        generatedAt = [DateTimeOffset]::Now.ToString("O")
        status = "PASS"
        manifest = [ordered]@{ path = $manifestPath; sha256 = $manifestHash }
        database = [ordered]@{ image = $PostgresImage; flywayVersion = 207; isolated = $true }
        expected = [ordered]@{ inputRows = $expectedRows; canonicalProducts = $expectedCanonical; crossMarketMerges = $expectedMerges }
        firstPass = $firstPass
        firstPassDatabase = [ordered]@{ products = $firstDatabaseCount; marketAvailabilityRows = $firstAvailabilityCount }
        searchSmoke = $searchResults
        secondPass = $secondPass
        secondPassDatabase = [ordered]@{ products = $secondDatabaseCount; marketAvailabilityRows = $secondAvailabilityCount }
        rollback = [ordered]@{ preImportSnapshotRestored = $true; restoredFoodItems = $rollbackCount }
    }
    Write-JsonFile -Path $reportPath -Value $report
    $report | ConvertTo-Json -Depth 10
}
finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
        $appProcess.WaitForExit()
    }
    @(
        "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
        "SPRING_PROFILES_ACTIVE", "SPRING_CACHE_TYPE", "GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED",
        "GRUN_LOCAL_ADMIN_EMAIL", "GRUN_LOCAL_ADMIN_PASSWORD", "GRUN_RATE_LIMIT_ENABLED",
        "GRUN_RATE_LIMIT_REDIS_ENABLED", "SERVER_PORT", "SPRING_JPA_SHOW_SQL"
    ) | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
    $running = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}" 2>$null
    if ($running -eq $containerName) { docker stop $containerName *> $null }
    Pop-Location
}
