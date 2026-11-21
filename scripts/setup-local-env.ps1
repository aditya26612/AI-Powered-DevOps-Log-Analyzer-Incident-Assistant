# ==============================================================================
# DevInsight - Local Environment Setup & Secret Management (PowerShell)
# ==============================================================================
# Purpose: One-time persistent setup for local development. Generates high-entropy
# cryptographically secure keys, prompts for existing PostgreSQL credentials once,
# validates connectivity, and saves configuration with restricted user permissions.
# ==============================================================================

[CmdletBinding()]
param (
    [switch]$Force,
    [string]$DbPasswordInput = "",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = "postgres"
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$EnvLocalPath = Join-Path $RootDir ".env.local"

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "           DEVINSIGHT - LOCAL ENVIRONMENT SETUP & CONFIGURATION      " -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# Helper: Generate cryptographically secure high-entropy hex string
# ------------------------------------------------------------------------------
function New-SecureToken([int]$byteCount = 32) {
    $bytes = New-Object byte[] $byteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

# ------------------------------------------------------------------------------
# 1. Load existing configuration if present (idempotent setup)
# ------------------------------------------------------------------------------
$config = @{}
if (Test-Path -LiteralPath $EnvLocalPath) {
    Write-Host "`nFound existing local configuration at .env.local" -ForegroundColor Gray
    Get-Content -LiteralPath $EnvLocalPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $val = $line -split "=", 2
            $config[$key.Trim()] = $val.Trim()
        }
    }
}

# ------------------------------------------------------------------------------
# 2. Stable JWT Signing Secret (>= 256 bits / 32 bytes)
# ------------------------------------------------------------------------------
$jwtSecret = $config["JWT_SECRET"]
if ($Force -or [string]::IsNullOrWhiteSpace($jwtSecret)) {
    $jwtSecret = New-SecureToken 32
    Write-Host "[GENERATED] High-entropy JWT signing secret (256-bit secure key)" -ForegroundColor Green
} else {
    Write-Host "[REUSED] Existing JWT signing secret preserved" -ForegroundColor Gray
}

# ------------------------------------------------------------------------------
# 3. Stable Internal Service API Key (>= 256 bits / 32 bytes)
# ------------------------------------------------------------------------------
$internalApiKey = $config["INTERNAL_API_KEY"]
if ($Force -or [string]::IsNullOrWhiteSpace($internalApiKey)) {
    $internalApiKey = New-SecureToken 32
    Write-Host "[GENERATED] High-entropy Internal API key (256-bit secure key)" -ForegroundColor Green
} else {
    Write-Host "[REUSED] Existing Internal API key preserved" -ForegroundColor Gray
}

# ------------------------------------------------------------------------------
# 4. Persistent webhook encryption key and PostgreSQL configuration
# ------------------------------------------------------------------------------
$webhookEncryptionKey = $config["SLACK_WEBHOOK_ENCRYPTION_KEY"]
if (-not $webhookEncryptionKey) {
    $webhookEncryptionKey = New-SecureToken 32
}
if ($webhookEncryptionKey -notmatch '^[0-9a-fA-F]{64}$') {
    throw 'Existing SLACK_WEBHOOK_ENCRYPTION_KEY has an invalid format. Restore it from the protected configuration.'
}
$runtimeSettings = @(
    foreach ($name in 'LOG_LAYER_DB_USERNAME', 'LOG_LAYER_DB_PASSWORD') {
        if ($config[$name]) { "$name=$($config[$name])" }
    }
) -join [Environment]::NewLine

$dbPassword = $config["DB_PASSWORD"]
if (-not [string]::IsNullOrWhiteSpace($DbPasswordInput)) {
    $dbPassword = $DbPasswordInput
} elseif ($Force -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    Write-Host "`nPostgreSQL Configuration:" -ForegroundColor White
    Write-Host "Please enter your local PostgreSQL password for user '$DbUser'." -ForegroundColor Gray
    Write-Host '(Press Enter to use DB_PASSWORD from the environment when set.)' -ForegroundColor Gray
    $promptSecure = Read-Host "Enter PostgreSQL password" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($promptSecure)
    $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    
    if (-not [string]::IsNullOrWhiteSpace($plain)) {
        $dbPassword = $plain
    } elseif ($env:DB_PASSWORD) {
        $dbPassword = $env:DB_PASSWORD
    } else {
        throw 'Set DB_PASSWORD or enter the local PostgreSQL password when prompted.'
    }
}

# ------------------------------------------------------------------------------
# 5. Pre-flight connectivity checks
# ------------------------------------------------------------------------------
Write-Host "`nValidating local dependencies..." -ForegroundColor White

# PostgreSQL port check
$pgCheck = Test-NetConnection -ComputerName $DbHost -Port $DbPort -WarningAction SilentlyContinue
if ($pgCheck.TcpTestSucceeded) {
    Write-Host "  [OK] PostgreSQL listening on ${DbHost}:${DbPort}" -ForegroundColor Green
} else {
    Write-Host "  [WARNING] PostgreSQL is not reachable on ${DbHost}:${DbPort}." -ForegroundColor Yellow
    Write-Host "            Ensure the PostgreSQL service or container is started." -ForegroundColor Yellow
}

# Ollama port check
$ollamaCheck = Test-NetConnection -ComputerName "localhost" -Port 11434 -WarningAction SilentlyContinue
if ($ollamaCheck.TcpTestSucceeded) {
    Write-Host "  [OK] Ollama daemon listening on localhost:11434" -ForegroundColor Green
} else {
    Write-Host "  [INFO] Ollama is not listening on localhost:11434." -ForegroundColor Yellow
    Write-Host "         LLM RAG features will be unavailable until Ollama is started." -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# 6. Assemble configuration content
# ------------------------------------------------------------------------------
$envContent = @"
# ==============================================================================
# DevInsight - Local Development Configuration (.env.local)
# GENERATED AUTOMATICALLY BY scripts/setup-local-env.ps1
# NOTE: This file contains local development secrets and MUST NOT be committed.
# ==============================================================================

# Database Configuration
DB_HOST=$DbHost
DB_PORT=$DbPort
DB_USERNAME=$DbUser
DB_PASSWORD=$dbPassword
$runtimeSettings

# Cryptographic Secrets (Persistent across restarts)
JWT_SECRET=$jwtSecret
INTERNAL_API_KEY=$internalApiKey
SLACK_WEBHOOK_ENCRYPTION_KEY=$webhookEncryptionKey

# Machine Learning & AI Runtime
ACTIVE_MODEL=v2
OLLAMA_BASE_URL=http://localhost:11434

# Inter-Service Gateway Routing URLs
USER_SERVICE_URL=http://localhost:8081
LOG_SERVICE_URL=http://localhost:8082
ML_SERVICE_URL=http://localhost:8000
LLM_SERVICE_URL=http://localhost:8084

# Incident & Notification Policy
ENVIRONMENT=development
NOTIFICATION_AUTO_DIAGNOSE=true
NOTIFICATION_SLACK_ENABLED=false
NOTIFICATION_SLACK_WEBHOOK_URL=
NOTIFICATION_SLACK_CHANNEL=#all-devinsight
NOTIFICATION_EMAIL_ENABLED=false
"@

# Write to .env.local
[System.IO.File]::WriteAllText($EnvLocalPath, $envContent, [System.Text.Encoding]::UTF8)

# ------------------------------------------------------------------------------
# 7. Restrict NTFS permissions to current Windows user
# ------------------------------------------------------------------------------
try {
    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $icaclsOutput = & icacls $EnvLocalPath /inheritance:r /grant:r "$($currentUser):(F)" 2>&1
    Write-Host "`n[SECURED] Restricted filesystem permissions on .env.local strictly to user: $currentUser" -ForegroundColor Green
} catch {
    Write-Host "`n[NOTICE] File created. Could not adjust NTFS ACL: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "`n=====================================================================" -ForegroundColor Cyan
Write-Host "                   SETUP COMPLETED SUCCESSFULLY!                    " -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "Configuration saved to: $EnvLocalPath" -ForegroundColor White
Write-Host "`nNext steps:" -ForegroundColor White
Write-Host "  1. Verify configuration:    .\scripts\check-local-env.ps1" -ForegroundColor Yellow
Write-Host "  2. Start DevInsight:        .\scripts\start-all.ps1" -ForegroundColor Yellow
Write-Host "=====================================================================" -ForegroundColor Cyan
