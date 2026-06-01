param(
    [string]$EnvPath = ".env",
    [switch]$VerboseOutput
)

$ErrorActionPreference = "Stop"

function Resolve-EnvValue {
    param([string]$RawValue)

    if ($null -eq $RawValue) {
        return ""
    }

    $value = $RawValue.Trim()

    # Remove optional leading "export " style values if someone writes: KEY=export value
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    # Common escaped newlines in .env values
    $value = $value -replace '\\n', "`n"

    return $value
}

if (-not (Test-Path -LiteralPath $EnvPath)) {
    throw ".env file not found: $EnvPath"
}

$loaded = 0
$skipped = 0
$lineNumber = 0

Get-Content -LiteralPath $EnvPath | ForEach-Object {
    $lineNumber++
    $line = $_

    if ([string]::IsNullOrWhiteSpace($line)) {
        $skipped++
        return
    }

    $trimmed = $line.Trim()

    if ($trimmed.StartsWith("#")) {
        $skipped++
        return
    }

    # Supports lines like: export KEY=value
    if ($trimmed -match '^export\s+') {
        $trimmed = $trimmed -replace '^export\s+', ''
    }

    if ($trimmed -notmatch '^[A-Za-z_][A-Za-z0-9_]*\s*=') {
        $skipped++
        if ($VerboseOutput) {
            Write-Host "Skipped invalid .env line $lineNumber"
        }
        return
    }

    $name, $rawValue = $trimmed -split '=', 2
    $name = $name.Trim()
    $value = Resolve-EnvValue -RawValue $rawValue

    [Environment]::SetEnvironmentVariable($name, $value, "Process")
    Set-Item -Path "Env:$name" -Value $value
    $loaded++

    if ($VerboseOutput) {
        Write-Host "Loaded env: $name"
    }
}

Write-Host "Loaded $loaded environment variable(s) from $EnvPath. Skipped $skipped line(s)."
