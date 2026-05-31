param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "",
    [string]$Password = "StrongPass1!",
    [switch]$SkipResend
)

$ErrorActionPreference = "Stop"

function Require-Env([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required env is missing: $Name"
    }
    return $value
}

function Invoke-JsonPost([string]$Url, [hashtable]$Body) {
    return Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10)
}

Write-Host "=== Brevo Smoke Test ==="

$provider = Require-Env "GRUN_MAIL_PROVIDER"
if ($provider -ne "BREVO") {
    throw "GRUN_MAIL_PROVIDER must be BREVO. Current: $provider"
}

$null = Require-Env "GRUN_MAIL_FROM_EMAIL"
$null = Require-Env "GRUN_BREVO_API_KEY"

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = "smoke-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + "@example.com"
}

Write-Host "Base URL: $BaseUrl"
Write-Host "Test Email: $Email"

$registerUrl = "$BaseUrl/api/v1/auth/register"
$resetUrl = "$BaseUrl/api/v1/auth/password-reset/request"
$resendUrl = "$BaseUrl/api/v1/auth/email-verification/resend"

try {
    $register = Invoke-JsonPost -Url $registerUrl -Body @{
        email = $Email
        password = $Password
    }
    Write-Host "Register: OK"
    Write-Host "Register message: $($register.message)"
} catch {
    Write-Host "Register failed: $($_.Exception.Message)"
    throw
}

try {
    $reset = Invoke-JsonPost -Url $resetUrl -Body @{
        email = $Email
    }
    Write-Host "Password reset request: OK"
    Write-Host "Password reset message: $($reset.message)"
} catch {
    Write-Host "Password reset request failed: $($_.Exception.Message)"
    throw
}

if (-not $SkipResend) {
    try {
        $resend = Invoke-JsonPost -Url $resendUrl -Body @{
            email = $Email
        }
        Write-Host "Email verification resend: OK"
        Write-Host "Resend message: $($resend.message)"
    } catch {
        Write-Host "Email verification resend failed: $($_.Exception.Message)"
        throw
    }
}

Write-Host ""
Write-Host "Manual check required:"
Write-Host "1. Confirm verification email reached inbox."
Write-Host "2. Confirm password reset email reached inbox."
Write-Host "3. Confirm links contain tokens and use expected base URLs."
Write-Host "4. Complete confirm endpoints with real token values if needed."
