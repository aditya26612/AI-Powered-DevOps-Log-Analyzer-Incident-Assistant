# ==============================================================================
# DevInsight - Local Environment Configuration Verifier (PowerShell)
# ==============================================================================
# Inspects environment readiness, dependency reachability, and secret entropy.
# NEVER prints raw secret values to console or output streams.
# ==============================================================================

[CmdletBinding()]
param ()

$ErrorActionPreference = "Continue"
$RootDir = Split-Path -Parent $PSScriptRoot
$EnvLocalPath = Join-Path $RootDir ".env.local"

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "             DEVINSIGHT - LOCAL ENVIRONMENT STATUS CHECK             " -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan

# 1. Load configuration from .env.local if present
$loadedFrom = ""
$config = @{}

if (Test-Path -LiteralPath $EnvLocalPath) {
    $loadedFrom = ".env.local"
    Get-Content -LiteralPath $EnvLocalPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $val = $line -split "=", 2
            $config[$key.Trim()] = $val.Trim()
        }
    }
}

# Helper to resolve key (env var first, then .env.local)
function Get-ConfigVal([string]$name) {
    $envVal = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrWhiteSpace($envVal)) {
        return $envVal
    }
    if ($config.ContainsKey($name)) {
        return $config[$name]
    }
    return ""
}

Write-Host "`n[1/3] Configuration & Secret Inventory:" -ForegroundColor White
if ($loadedFrom) {
    Write-Host "  Source: $loadedFrom (Persistent local file)" -ForegroundColor Gray
} else {
    Write-Host "  Source: Shell Environment Variables" -ForegroundColor Gray
}

# Verify JWT Secret
$jwtSecret = Get-ConfigVal "JWT_SECRET"
if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
    Write-Host "  JWT_SECRET                 : [MISSING] Run .\scripts\setup-local-env.ps1" -ForegroundColor Red
} elseif ([Text.Encoding]::UTF8.GetByteCount($jwtSecret) -lt 32) {
    Write-Host "  JWT_SECRET                 : [INVALID] Minimum 32 UTF-8 bytes required" -ForegroundColor Red
} else {
    Write-Host "  JWT_SECRET                 : [CONFIGURED] (length validated)" -ForegroundColor Green
}

# Verify Internal API Key
$internalKey = Get-ConfigVal "INTERNAL_API_KEY"
if ([string]::IsNullOrWhiteSpace($internalKey)) {
    Write-Host "  INTERNAL_API_KEY           : [MISSING] Run .\scripts\setup-local-env.ps1" -ForegroundColor Red
} elseif ($internalKey.Length -lt 16) {
    Write-Host "  INTERNAL_API_KEY           : [INVALID] Insufficient length ($($internalKey.Length) chars; minimum 16 chars required)" -ForegroundColor Red
} else {
    Write-Host "  INTERNAL_API_KEY           : [CONFIGURED] (length validated)" -ForegroundColor Green
}

# Verify PostgreSQL Password
$dbPass = Get-ConfigVal "DB_PASSWORD"
if ([string]::IsNullOrWhiteSpace($dbPass)) {
    Write-Host "  DB_PASSWORD                : [MISSING] Run .\scripts\setup-local-env.ps1" -ForegroundColor Red
} else {
    Write-Host "  DB_PASSWORD                : [CONFIGURED] (Protected at rest in .env.local)" -ForegroundColor Green
}

# Stage 02 runtime configuration. Print status without credential values.
$logRole = Get-ConfigVal "LOG_LAYER_DB_USERNAME"
$logPassword = Get-ConfigVal "LOG_LAYER_DB_PASSWORD"
if ($logRole -and $logPassword) {
    Write-Host "  Log Layer database login   : [CONFIGURED]" -ForegroundColor Green
} else {
    Write-Host "  Log Layer database login   : [MISSING] Follow the Stage 02 migration setup" -ForegroundColor Red
}
$webhookKey = Get-ConfigVal "SLACK_WEBHOOK_ENCRYPTION_KEY"
if ($webhookKey -match '^[0-9a-fA-F]{64}$') {
    Write-Host "  Slack encryption key       : [CONFIGURED] (format validated)" -ForegroundColor Green
} else {
    Write-Host "  Slack encryption key       : [MISSING OR INVALID] Follow the Stage 02 setup" -ForegroundColor Red
}

# Verify Active ML Model
$activeModel = Get-ConfigVal "ACTIVE_MODEL"
if ([string]::IsNullOrWhiteSpace($activeModel)) {
    Write-Host "  ACTIVE_MODEL               : [DEFAULT: v2] CuratedIsolationForest (40 features, threshold -0.02)" -ForegroundColor Gray
} else {
    Write-Host "  ACTIVE_MODEL               : [CONFIGURED: $activeModel]" -ForegroundColor Green
}

# Verify Slack Integration
$slackEnabled = Get-ConfigVal "NOTIFICATION_SLACK_ENABLED"
$slackWebhook = Get-ConfigVal "NOTIFICATION_SLACK_WEBHOOK_URL"
if ($slackEnabled -eq "true" -and -not [string]::IsNullOrWhiteSpace($slackWebhook)) {
    Write-Host "  NOTIFICATION_SLACK         : [CONFIGURED] (Alerts enabled)" -ForegroundColor Yellow
} else {
    Write-Host "  NOTIFICATION_SLACK         : [DISABLED / SAFE] (No external notifications sent during local testing)" -ForegroundColor Gray
}

# ------------------------------------------------------------------------------
# 2. Dependency Reachability Checks
# ------------------------------------------------------------------------------
Write-Host "`n[2/3] Local Dependency Reachability:" -ForegroundColor White

$dbHost = if (Get-ConfigVal "DB_HOST") { Get-ConfigVal "DB_HOST" } else { "localhost" }
$dbPort = if (Get-ConfigVal "DB_PORT") { [int](Get-ConfigVal "DB_PORT") } else { 5432 }

$pgTest = Test-NetConnection -ComputerName $dbHost -Port $dbPort -WarningAction SilentlyContinue
if ($pgTest.TcpTestSucceeded) {
    Write-Host "  PostgreSQL (${dbHost}:${dbPort})     : [REACHABLE]" -ForegroundColor Green
} else {
    Write-Host "  PostgreSQL (${dbHost}:${dbPort})     : [SERVICE UNAVAILABLE] Start PostgreSQL service/container" -ForegroundColor Red
}

$ollamaTest = Test-NetConnection -ComputerName "localhost" -Port 11434 -WarningAction SilentlyContinue
if ($ollamaTest.TcpTestSucceeded) {
    Write-Host "  Ollama (localhost:11434)   : [REACHABLE]" -ForegroundColor Green
    # Check loaded models
    try {
        $tags = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -Method Get -TimeoutSec 3
        $models = ($tags.models | ForEach-Object { $_.name }) -join ", "
        Write-Host "    Available models: $models" -ForegroundColor Gray
    } catch {
        Write-Host "    Could not query /api/tags: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Ollama (localhost:11434)   : [SERVICE UNAVAILABLE] LLM diagnosis will fail closed until daemon started" -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# 3. Overall Readiness Summary
# ------------------------------------------------------------------------------
Write-Host "`n[3/3] Overall Readiness:" -ForegroundColor White
$ready = (-not [string]::IsNullOrWhiteSpace($jwtSecret)) -and `
         ([Text.Encoding]::UTF8.GetByteCount($jwtSecret) -ge 32) -and `
         (-not [string]::IsNullOrWhiteSpace($internalKey)) -and `
         ($internalKey.Length -ge 16) -and `
         (-not [string]::IsNullOrWhiteSpace($dbPass)) -and `
         ($pgTest.TcpTestSucceeded)

if ($ready) {
    Write-Host "  STATUS: READY TO LAUNCH" -ForegroundColor Green
    Write-Host "  Run '.\scripts\start-all.ps1' to boot all microservices." -ForegroundColor White
} else {
    Write-Host "  STATUS: CONFIGURATION INCOMPLETE" -ForegroundColor Red
    Write-Host "  Run '.\scripts\setup-local-env.ps1' to resolve missing configurations." -ForegroundColor Yellow
}

Write-Host "=====================================================================" -ForegroundColor Cyan
