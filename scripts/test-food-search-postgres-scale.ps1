param(
    [int]$DatabasePort = 55436,
    [int]$ApplicationPort = 18081,
    [string]$PostgresImage = "postgres:16-alpine",
    [int]$P95BudgetMs = 300,
    [switch]$ReuseExistingJar
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$containerName = "grun-product-search-scale-postgres"
$databaseName = "grun_product_search_scale"
$databaseUser = "scale"
$databasePassword = "scale-local-only"
$adminEmail = "s11-admin@grun.local"
$adminPassword = "S11-Local-Only-Pass1!"
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot "outputs/product-data-readiness/s11-scale"
$metricsPath = Join-Path $outputDirectory "metrics.json"
$appLogPath = Join-Path $outputDirectory "application.log"
$appErrorPath = Join-Path $outputDirectory "application-error.log"
$seedPath = Join-Path $PSScriptRoot "seed-food-search-scale.sql"
$appProcess = $null
$metrics = @()

function Invoke-DockerChecked {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed: docker $($Arguments -join ' ')" }
}

function Invoke-Psql {
    param([string]$Sql)
    $result = & docker exec $containerName psql -v ON_ERROR_STOP=1 -U $databaseUser -d $databaseName -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL command failed." }
    return $result
}

function Add-ScaleRows {
    param([int]$First, [int]$Last)
    Invoke-DockerChecked -Arguments @("cp", $seedPath, ($containerName + ":/tmp/seed-food-search-scale.sql"))
    Invoke-DockerChecked -Arguments @(
        "exec", $containerName, "psql",
        "-v", "ON_ERROR_STOP=1",
        "-U", $databaseUser,
        "-d", $databaseName,
        "-v", "first=$First",
        "-v", "last=$Last",
        "-f", "/tmp/seed-food-search-scale.sql"
    )
}

function Get-Percentile {
    param([long[]]$Values, [double]$Percentile)
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * $Percentile) - 1)
    return [long]$sorted[$index]
}

function Invoke-Search {
    param([hashtable]$Case, [int]$Page = 0)
    $encoded = [uri]::EscapeDataString($Case.query)
    $uri = "http://127.0.0.1:$ApplicationPort/api/v1/products/search?q=$encoded&region=$($Case.region)&language=$($Case.language)&page=$Page&size=25"
    return Invoke-RestMethod -Uri $uri -Headers @{ Authorization = "Bearer $script:token" } -Method Get
}

Push-Location $projectRoot
try {
    $portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $ApplicationPort)
    try {
        $portProbe.Start()
    } catch {
        throw "Application port $ApplicationPort is already in use."
    } finally {
        $portProbe.Stop()
    }

    $existing = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}"
    if ($LASTEXITCODE -ne 0) { throw "Docker is not available." }
    if ($existing) { throw "Container '$containerName' already exists." }

    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $hostPort = "127.0.0.1:" + $DatabasePort + ":5432"
    $dockerArguments = @(
        "run", "--rm", "-d", "--name", $containerName,
        "-e", "POSTGRES_USER=$databaseUser",
        "-e", "POSTGRES_PASSWORD=$databasePassword",
        "-e", "POSTGRES_DB=$databaseName",
        "-p", $hostPort, $PostgresImage
    )
    Invoke-DockerChecked -Arguments $dockerArguments | Out-Null

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $containerName pg_isready -U $databaseUser -d $databaseName *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Temporary PostgreSQL did not become ready." }

    $mavenWrapper = Join-Path $projectRoot "mvnw.cmd"
    if (-not $ReuseExistingJar) {
        & $mavenWrapper clean "-Dmaven.test.skip=true" package
        if ($LASTEXITCODE -ne 0) { throw "Application package build failed." }
    }
    $jar = Get-ChildItem "target/*.jar" | Where-Object { $_.Name -notlike "*.original" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "Application jar was not found." }
    if ($ReuseExistingJar) {
        $latestInput = Get-ChildItem (Join-Path $projectRoot "src/main") -Recurse -File |
            Sort-Object LastWriteTimeUtc -Descending |
            Select-Object -First 1
        $pom = Get-Item (Join-Path $projectRoot "pom.xml")
        $latestInputTime = @($latestInput.LastWriteTimeUtc, $pom.LastWriteTimeUtc) |
            Sort-Object -Descending |
            Select-Object -First 1
        if ($jar.LastWriteTimeUtc -lt $latestInputTime) {
            throw "Existing jar is older than the current main source or pom.xml. Run without -ReuseExistingJar."
        }
    }

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

    [IO.File]::WriteAllText($appLogPath, "")
    [IO.File]::WriteAllText($appErrorPath, "")
    $javaSettings = & cmd /c "java -XshowSettings:properties -version 2>&1"
    $javaHomeLine = $javaSettings | Where-Object { $_ -match "^\s*java\.home\s*=" } | Select-Object -First 1
    if (-not $javaHomeLine) { throw "Could not resolve java.home." }
    $javaHome = ($javaHomeLine -split "=", 2)[1].Trim()
    $javaExecutable = Join-Path $javaHome "bin/java.exe"
    $appProcess = Start-Process -FilePath $javaExecutable -ArgumentList @("-jar", $jar.FullName, "--server.port=$ApplicationPort") -RedirectStandardOutput $appLogPath -RedirectStandardError $appErrorPath -WindowStyle Hidden -PassThru
    $applicationReady = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if ($appProcess.HasExited) { throw "Scale application exited early. See $appLogPath" }
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$ApplicationPort/actuator/health" -TimeoutSec 2 | Out-Null
            $applicationReady = $true
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $applicationReady) { throw "Scale application did not become ready." }

    $loginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:$ApplicationPort/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
    $script:token = $login.token
    if (-not $script:token) { throw "Scale admin login did not return a token." }

    $searchCases = @(
        @{ query = "banana"; region = "UK_IE"; language = "EN"; expected = "Banana" },
        @{ query = "broccoli"; region = "UK_IE"; language = "EN"; expected = "Broccoli" },
        @{ query = "milk"; region = "UK_IE"; language = "EN"; expected = "Milk" },
        @{ query = "chicken breast"; region = "UK_IE"; language = "EN"; expected = "Chicken Breast" },
        @{ query = "rice"; region = "UK_IE"; language = "EN"; expected = "Rice" },
        @{ query = "muz"; region = "TR"; language = "TR"; expected = "Muz" },
        @{ query = "brokoli"; region = "TR"; language = "TR"; expected = "Brokoli" },
        @{ query = "sut"; region = "TR"; language = "TR"; expected = "Tam" },
        @{ query = "tavuk gogsu"; region = "TR"; language = "TR"; expected = "Tavuk" },
        @{ query = "pirinc"; region = "TR"; language = "TR"; expected = "Pirin" }
    )

    $previous = 0
    foreach ($target in @(25000, 50000, 100000, 200000)) {
        $seedWatch = [Diagnostics.Stopwatch]::StartNew()
        Add-ScaleRows -First ($previous + 1) -Last $target
        $seedWatch.Stop()
        $count = [long](Invoke-Psql "select count(*) from food_items where source_key like 'S11:%';")
        if ($count -ne $target) { throw "Expected $target products but found $count." }

        foreach ($case in $searchCases) { Invoke-Search $case | Out-Null }
        $durations = @()
        foreach ($iteration in 1..3) {
            foreach ($case in $searchCases) {
                $watch = [Diagnostics.Stopwatch]::StartNew()
                $result = Invoke-Search $case
                $watch.Stop()
                if (-not $result.content -or -not $result.content[0].productName.Contains($case.expected)) {
                    throw "Unexpected result for '$($case.query)'."
                }
                $durations += $watch.ElapsedMilliseconds
            }
        }

        $p50 = Get-Percentile $durations 0.50
        $p95 = Get-Percentile $durations 0.95
        $max = ($durations | Measure-Object -Maximum).Maximum
        $rowsPerSecond = [Math]::Round(($target - $previous) * 1000.0 / [Math]::Max(1, $seedWatch.ElapsedMilliseconds), 1)
        $appProcess.Refresh()
        $metric = [pscustomobject]@{
            products = $target
            p50Ms = $p50
            p95Ms = $p95
            maxMs = $max
            seedMs = $seedWatch.ElapsedMilliseconds
            seedRowsPerSecond = $rowsPerSecond
            applicationWorkingSetMb = [Math]::Round($appProcess.WorkingSet64 / 1MB, 1)
        }
        $metrics += $metric
        Write-Output ("S11_SCALE_METRIC " + ($metric | ConvertTo-Json -Compress))
        if ($p95 -gt $P95BudgetMs) { throw "$target product search p95 $p95 ms exceeded $P95BudgetMs ms." }
        $previous = $target
    }

    $deepPage = Invoke-Search $searchCases[2] 2
    if (-not $deepPage.content) { throw "Deep pagination returned no content." }

    $client = [Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $script:token)
    $concurrentWatch = [Diagnostics.Stopwatch]::StartNew()
    $tasks = foreach ($case in $searchCases) {
        $encoded = [uri]::EscapeDataString($case.query)
        $uri = "http://127.0.0.1:$ApplicationPort/api/v1/products/search?q=$encoded&region=$($case.region)&language=$($case.language)&page=0&size=25"
        $client.GetAsync($uri)
    }
    [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]$tasks)
    $concurrentWatch.Stop()
    foreach ($task in $tasks) { if (-not $task.Result.IsSuccessStatusCode) { throw "Concurrent request failed." } }
    $client.Dispose()
    if ($concurrentWatch.ElapsedMilliseconds -gt 1200) { throw "Concurrent HTTP batch exceeded 1200 ms." }

    $indexSql = "select count(*) from pg_indexes where indexname in ('idx_food_items_name_trgm','idx_food_items_display_name_trgm','idx_food_items_short_display_name_trgm','idx_food_items_brand_trgm','idx_food_item_aliases_alias_lower_trgm','idx_food_item_localizations_display_lower_trgm','idx_food_item_localizations_short_lower_trgm');"
    $indexCount = [int](Invoke-Psql $indexSql)
    if ($indexCount -ne 7) { throw "Expected 7 search indexes but found $indexCount." }

    $planSql = "explain (analyze, buffers, format text) select id from food_items where lower(name) like '%milk%' or lower(display_name) like '%milk%' or lower(short_display_name) like '%milk%' or lower(brand) like '%milk%' order by id limit 5001;"
    $plan = @(Invoke-Psql $planSql)
    $planText = $plan -join [Environment]::NewLine
    if ($planText -notmatch "Bitmap Index Scan|idx_food_items") { throw "EXPLAIN ANALYZE did not use search indexes." }

    $report = [pscustomobject]@{
        generatedAt = [DateTimeOffset]::Now.ToString("O")
        p95BudgetMs = $P95BudgetMs
        scales = $metrics
        deepPagination = @{ page = $deepPage.page; size = $deepPage.content.Count; total = $deepPage.totalElements }
        concurrentBatch = @{ requests = $searchCases.Count; elapsedMs = $concurrentWatch.ElapsedMilliseconds }
        indexCount = $indexCount
        explainPlan = $plan
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metricsPath -Encoding utf8
    Write-Output "S11_SCALE_GATE_PASSED report=$metricsPath"
}
finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
        $appProcess.WaitForExit()
    }
    @("SPRING_DATASOURCE_URL","SPRING_DATASOURCE_USERNAME","SPRING_DATASOURCE_PASSWORD","SPRING_PROFILES_ACTIVE","SPRING_CACHE_TYPE","GRUN_LOCAL_ADMIN_BOOTSTRAP_ENABLED","GRUN_LOCAL_ADMIN_EMAIL","GRUN_LOCAL_ADMIN_PASSWORD","GRUN_RATE_LIMIT_ENABLED","GRUN_RATE_LIMIT_REDIS_ENABLED","SERVER_PORT") | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
    $running = docker ps -a --filter "name=^/$containerName$" --format "{{.Names}}" 2>$null
    if ($running -eq $containerName) { docker stop $containerName *> $null }
    Pop-Location
}
