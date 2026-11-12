# ==============================================================================
# DevInsight - Live Spring Boot Ingestion & Alerting Test
# ==============================================================================
# This script simulates a running Spring Boot application sending a real multiline
# database connection failure directly to DevInsight via the API Gateway.
# ==============================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ApiKey = "devinsight_internal_ml_key_secret_12345",
    [string]$ServiceName = "order-payment-service"
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " [SIMULATION] Live Spring Boot Application Streaming Error to DevInsight" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

$logPayload = @"
2026-11-12T13:00:00.123+05:30 ERROR 4201 --- [http-nio-8080-exec-1] com.project.service.OrderService : Critical failure processing checkout order #994812
org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for non-replication superuser connections
	at org.postgresql.core.v3.ConnectionFactoryImpl.doAuthentication(ConnectionFactoryImpl.java:684)
	at org.postgresql.core.v3.ConnectionFactoryImpl.tryConnect(ConnectionFactoryImpl.java:204)
	at org.postgresql.Driver.makeConnection(Driver.java:441)
	at com.zaxxer.hikari.pool.PoolBase.newConnection(PoolBase.java:364)
	at com.zaxxer.hikari.pool.HikariPool.createPoolEntry(HikariPool.java:478)
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:188)
"@

$body = @{
    rawLog = $logPayload
    source = "SPRING_BOOT"
    environment = "PRODUCTION"
    applicationName = $ServiceName
} | ConvertTo-Json

Write-Host "[1/3] Sending real-time log event to $GatewayUrl/api/v1/logs..." -ForegroundColor Gray
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $headers = @{
        "Content-Type" = "application/json"
        "X-API-Key" = $ApiKey
        "X-User-Email" = "devops-alerts@company.com"
    }

    $response = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/logs" -Method Post -Headers $headers -Body $body
    $stopwatch.Stop()

    Write-Host "[SUCCESS] Log Ingested in $($stopwatch.ElapsedMilliseconds) ms!" -ForegroundColor Green
    Write-Host "-----------------------------------------------------------------"
    Write-Host " Log ID:              $($response.id)" -ForegroundColor White
    Write-Host " Correlation ID:      $($response.correlationId)" -ForegroundColor White
    Write-Host " ML Anomaly Detected: $($response.anomaly)" -ForegroundColor $(if ($response.anomaly) { "Red" } else { "Green" })
    Write-Host " ML Prediction:       $($response.predictionLabel) (Score: $($response.decisionScore))" -ForegroundColor Yellow
    Write-Host " Status:              $($response.status)" -ForegroundColor White
    Write-Host " Analysis Status:     $($response.analysisStatus)" -ForegroundColor White
    Write-Host "-----------------------------------------------------------------"

    Write-Host "[2/3] Automated LLM Incident Diagnosis has been queued asynchronously in the background." -ForegroundColor Cyan
    Write-Host "[3/3] When complete, DevInsight will:" -ForegroundColor Yellow
    Write-Host "      - Update the log with Root Cause & Suggested Remediation" -ForegroundColor White
    Write-Host "      - Dispatch rich Block Kit notification to Slack / Console" -ForegroundColor White
    Write-Host "      - Display in Dashboard: http://localhost:5173/logs/$($response.id)" -ForegroundColor White

} catch {
    $stopwatch.Stop()
    Write-Host "[FAILED] Error communicating with DevInsight: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Response Body: $($reader.ReadToEnd())" -ForegroundColor Red
    }
}

