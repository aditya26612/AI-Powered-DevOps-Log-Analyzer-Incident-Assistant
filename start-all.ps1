# ==============================================================================
# DevInsight - Complete One-Click Orchestrator
# ==============================================================================

param (
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$JwtSecret = $env:JWT_SECRET,
    [string]$InternalApiKey = $env:INTERNAL_API_KEY
)

Clear-Host

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "             DEVINSIGHT - ALL-IN-ONE SYSTEM LAUNCHER                 " -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan

# 1. Credentials
if (-not $DbPassword) {
    $DbPassword = Read-Host "Enter PostgreSQL password (Enter for postgres)"
    if (-not $DbPassword) {
        $DbPassword = "postgres"
    }
}

if (-not $JwtSecret) {
    $JwtSecret = "devinsight_super_secret_jwt_key_2026_at_least_32_characters_long!"
}

if (-not $InternalApiKey) {
    $InternalApiKey = "devinsight_internal_ml_key_secret_12345"
}

$RootDir = $PSScriptRoot

Write-Host "`n[1/3] Environment Configuration" -ForegroundColor White
Write-Host "DB_PASSWORD      : [CONFIGURED]" -ForegroundColor Gray
Write-Host "JWT_SECRET       : [CONFIGURED]" -ForegroundColor Gray
Write-Host "INTERNAL_API_KEY : [CONFIGURED]" -ForegroundColor Gray

# 2. Pre-flight checks
Write-Host "`n[2/3] Performing Pre-flight Checks..." -ForegroundColor White

foreach ($check in @(
    @{ Name = "PostgreSQL"; Port = 5432 },
    @{ Name = "Ollama"; Port = 11434 }
)) {
    $result = Test-NetConnection localhost -Port $check.Port -WarningAction SilentlyContinue

    if ($result.TcpTestSucceeded) {
        Write-Host "  [OK] $($check.Name) detected on port $($check.Port)" -ForegroundColor Green
    }
    else {
        Write-Host "  [WARNING] $($check.Name) is not listening on port $($check.Port)" -ForegroundColor Yellow
    }
}

# 3. Service launcher
function ConvertTo-PsLiteral {
    param([string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Start-ServiceWindow {
    param (
        [string]$Title,
        [string]$Directory,
        [string]$Banner,
        [string]$Command,
        [hashtable]$Environment = @{}
    )

    $parts = @(
        '$Host.UI.RawUI.WindowTitle = ' + (ConvertTo-PsLiteral $Title)
        'Set-Location -LiteralPath ' + (ConvertTo-PsLiteral $Directory)
    )

    foreach ($key in $Environment.Keys) {
        $parts += ('$env:' + $key + ' = ' + (ConvertTo-PsLiteral ([string]$Environment[$key])))
    }

    $parts += 'Write-Host ' + (ConvertTo-PsLiteral $Banner) + ' -ForegroundColor Cyan'
    $parts += $Command

    $script = $parts -join "`n"

    $encoded = [Convert]::ToBase64String(
        [Text.Encoding]::Unicode.GetBytes($script)
    )

    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoExit",
        "-EncodedCommand",
        $encoded
    )
}

Write-Host "`n[3/3] Starting Application Services..." -ForegroundColor White

# ML Pipeline
Write-Host "Launching ML Pipeline :8000..." -ForegroundColor Cyan

Start-ServiceWindow `
    -Title "DevInsight: ML Pipeline (:8000)" `
    -Directory (Join-Path $RootDir "ml-pipeline") `
    -Banner "ML PIPELINE (FastAPI :8000)" `
    -Command "python -m uvicorn app.main:app --host 0.0.0.0 --port 8000" `
    -Environment @{
        INTERNAL_API_KEY = $InternalApiKey
    }

# User Service
Write-Host "Launching User Service :8081..." -ForegroundColor Green

Start-ServiceWindow `
    -Title "DevInsight: User Service (:8081)" `
    -Directory $RootDir `
    -Banner "USER SERVICE (:8081)" `
    -Command "mvn -f user-service\pom.xml spring-boot:run" `
    -Environment @{
        DB_PASSWORD = $DbPassword
        JWT_SECRET = $JwtSecret
    }

# Log Layer
Write-Host "Launching Log Layer :8082..." -ForegroundColor Magenta

Start-ServiceWindow `
    -Title "DevInsight: Log Layer (:8082)" `
    -Directory $RootDir `
    -Banner "LOG LAYER (:8082)" `
    -Command "mvn -f log-layer\pom.xml spring-boot:run" `
    -Environment @{
        DB_PASSWORD = $DbPassword
        JWT_SECRET = $JwtSecret
        INTERNAL_API_KEY = $InternalApiKey
    }

# LLM Service
Write-Host "Launching LLM Service :8084..." -ForegroundColor Yellow

Start-ServiceWindow `
    -Title "DevInsight: LLM Service (:8084)" `
    -Directory $RootDir `
    -Banner "LLM SERVICE (:8084)" `
    -Command "mvn -f llm-service\pom.xml spring-boot:run" `
    -Environment @{
        DB_PASSWORD = $DbPassword
    }

# API Gateway
Write-Host "Launching API Gateway :8080..." -ForegroundColor Blue

Start-ServiceWindow `
    -Title "DevInsight: API Gateway (:8080)" `
    -Directory $RootDir `
    -Banner "API GATEWAY (:8080)" `
    -Command "mvn -f api-gateway\pom.xml spring-boot:run" `
    -Environment @{
        JWT_SECRET = $JwtSecret
    }

# Frontend
Write-Host "Launching React Frontend :5173..." -ForegroundColor Green

Start-ServiceWindow `
    -Title "DevInsight: Frontend (:5173)" `
    -Directory (Join-Path $RootDir "frontend") `
    -Banner "FRONTEND (Vite :5173)" `
    -Command "npm run dev"

# Final information
Write-Host "`n=====================================================================" -ForegroundColor Cyan
Write-Host "             DEVINSIGHT PLATFORM IS STARTING!                        " -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Cyan

Write-Host "`nAccess URLs:" -ForegroundColor White
Write-Host "Frontend Dashboard : http://localhost:5173" -ForegroundColor Yellow
Write-Host "Live Log Inspector : http://localhost:5173/analyzer" -ForegroundColor Yellow
Write-Host "API Gateway        : http://localhost:8080" -ForegroundColor Yellow
Write-Host "ML Pipeline Docs   : http://localhost:8000/docs" -ForegroundColor Yellow

Write-Host "`nTo stop all services, run .\stop-all.ps1" -ForegroundColor Gray
Write-Host "=====================================================================" -ForegroundColor Cyan