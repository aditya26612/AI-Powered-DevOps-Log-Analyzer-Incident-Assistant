# Read-only by default. Opt in to creating smoke-test records in a local disposable stack.
[CmdletBinding()]
param(
    [uri]$GatewayUrl = 'http://127.0.0.1:8080',
    [string]$TestUserEmail,
    [switch]$SkipAuth,
    [switch]$RunWorkflow,
    [ValidateRange(1, 300)][int]$DiagnosisTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$root = Split-Path -Parent $PSScriptRoot
if (-not $GatewayUrl.IsAbsoluteUri -or -not $GatewayUrl.IsLoopback -or $GatewayUrl.UserInfo -or
    $GatewayUrl.Scheme -notin 'http', 'https' -or $GatewayUrl.AbsolutePath -ne '/' -or $GatewayUrl.Query -or $GatewayUrl.Fragment) {
    throw 'GatewayUrl must be a loopback HTTP origin.'
}
$gateway = $GatewayUrl.GetLeftPart([UriPartial]::Authority)

$healthy = $true
foreach ($service in Get-DevInsightServices) {
    $baseUrl = if ($service.Id -eq 'api-gateway') { $gateway } else { "http://127.0.0.1:$($service.Port)" }
    $result = Invoke-DevInsightProbe -Uri ($baseUrl + $service.Path) -Kind $service.Kind
    Write-Host "[$($result.Summary)] $($service.Name)"
    if (-not $result.Ready) { $healthy = $false }
}

try {
    $model = Invoke-RestMethod 'http://127.0.0.1:8000/api/v1/system/model' -TimeoutSec 3 -MaximumRedirection 0
    Write-Host "ML model: $($model.model_version), $($model.feature_count) features, active threshold $($model.active_threshold)"
    if ($model.model_version -ne '2.0.0-candidate' -or $model.feature_count -ne 40 -or $model.active_threshold -ne -0.02) {
        Write-Warning 'The active model does not match the documented local V2 baseline.'
        $healthy = $false
    }
} catch {
    Write-Warning 'ML model information is unavailable.'
    $healthy = $false
}

if ($SkipAuth -or -not $RunWorkflow) {
    Write-Host 'Read-only checks finished. Use -RunWorkflow with a disposable stack to verify ingestion and diagnosis.'
    if (-not $healthy) { exit 1 }
    exit 0
}

# The managed launcher disables Slack and email in the Log Layer. Verify that both
# listeners are owned by the same recorded local run before making any writes.
if ($GatewayUrl.Port -ne 8080 -or $GatewayUrl.Scheme -ne 'http') {
    throw 'The write workflow requires the managed local Gateway on HTTP port 8080.'
}
$lifecycleLock = Open-DevInsightLifecycleLock
try {
    $managedServices = @{}
    $disposablePort = $null
    $connections = @(Get-NetTCPConnection -State Listen)
    $processes = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CreationDate)
    foreach ($service in Get-DevInsightServices | Where-Object Id -in 'api-gateway', 'log-layer', 'user-service') {
        foreach ($record in Get-DevInsightRecords | Where-Object Service -eq $service.Id) {
            $managed = Get-DevInsightManagedProcess $record
            if ($managed -and (Test-DevInsightListener $managed $service.Port $connections $processes)) {
                $managedServices[$service.Id] = $record.RunId
                if ($service.Id -eq 'log-layer') { $disposablePort = $record.DatabasePort }
                break
            }
        }
        if (-not $managedServices.ContainsKey($service.Id)) {
            throw "$($service.Name) is not running under the local launcher. No test records were written."
        }
    }
    if (@($managedServices.Values | Sort-Object -Unique).Count -ne 1) {
        throw 'Gateway, Log Layer and User Service must belong to the same local launch.'
    }

    if (-not $disposablePort -or $disposablePort -eq 5432) {
        throw 'The write workflow requires a managed launch on a separate disposable PostgreSQL port.'
    }
    $requestOptions = @{ TimeoutSec = 8; MaximumRedirection = 0; ErrorAction = 'Stop' }

    $createAccount = -not $TestUserEmail
    if ($createAccount) {
        $TestUserEmail = "smoke-$([Guid]::NewGuid().ToString('N'))@devinsight.local"
        $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    } else {
        $password = $env:DEVINSIGHT_SMOKE_PASSWORD
        if (-not $password) { throw 'Set DEVINSIGHT_SMOKE_PASSWORD for the supplied test account.' }
    }
    $loginBody = @{ email = $TestUserEmail; password = $password } | ConvertTo-Json -Compress
    try {
        if ($createAccount) {
            $registered = Invoke-RestMethod "$gateway/api/v1/auth/register" -Method Post -Body $loginBody -ContentType 'application/json' @requestOptions
            if ($registered.role -ne 'DEVELOPER') { throw 'Unexpected registration role' }
            Write-Host '[OK] Smoke account registered as DEVELOPER.'
        }
        $login = Invoke-RestMethod "$gateway/api/v1/auth/login" -Method Post -Body $loginBody -ContentType 'application/json' @requestOptions
        if (-not $login.token) { throw 'Missing session token' }
        Write-Host '[OK] Login returned a session token.'
    } catch {
        throw 'Smoke authentication failed. Verify the selected account and local User Service.'
    }

    try {
        $bearer = @{ Authorization = "Bearer $($login.token)" }
        $slug = "smoke-$([Guid]::NewGuid().ToString('N'))"
        $orgBody = @{ name = 'Stage 02 smoke fixture'; slug = $slug } | ConvertTo-Json -Compress
        $org = Invoke-RestMethod "$gateway/api/v1/organizations" -Method Post -Headers $bearer -Body $orgBody -ContentType 'application/json' @requestOptions
        $apps = "$gateway/api/v1/organizations/$($org.id)/applications"
        $appBody = @{ applicationName = 'payment-service'; techStack = 'SPRING_BOOT'; environment = 'DEVELOPMENT' } | ConvertTo-Json -Compress
        $app = Invoke-RestMethod $apps -Method Post -Headers $bearer -Body $appBody -ContentType 'application/json' @requestOptions
        $keyBody = @{ name = 'Stage 02 smoke fixture'; expiresAt = [DateTime]::UtcNow.AddHours(1).ToString('o') } | ConvertTo-Json -Compress
        $credential = Invoke-RestMethod "$apps/$($app.id)/credentials" -Method Post -Headers $bearer -Body $keyBody -ContentType 'application/json' @requestOptions
        if (-not $credential.key) { throw 'Scoped credential missing' }
        $headers = @{ 'X-API-Key' = $credential.key }
        $readHeaders = @{ Authorization = $bearer.Authorization; 'X-DevInsight-Organization' = $org.id }
        Write-Host '[OK] Disposable workspace, application and scoped credential created.'
    } catch {
        throw 'Scoped smoke onboarding failed.'
    }

    $samples = @(
        @{ Name = 'Normal'; App = 'payment-service'; Message = '2026-11-20 12:00:00 INFO order-service - Payment transaction TX-9281 approved via Stripe gateway' },
        @{ Name = 'Operational error'; App = 'payment-service'; Message = '2026-11-20 12:01:00 ERROR payment-service - Database deadlock detected: SQL transaction aborted on postgres table payments sqlstate 40001' }
    )
    $diagnosisVerified = $true
    foreach ($sample in $samples) {
        $body = @{
            rawLog = $sample.Message
            source = 'SPRING_BOOT'
            applicationName = $app.applicationName
            applicationId = $app.id
            environment = 'DEVELOPMENT'
        } | ConvertTo-Json -Compress
        try {
            $ingested = Invoke-RestMethod "$gateway/api/v1/logs" -Method Post -Headers $headers -Body $body -ContentType 'application/json' @requestOptions
            if (-not $ingested.id) { throw 'Missing persisted log ID' }
        } catch {
            throw "$($sample.Name) ingestion failed."
        }
        Write-Host "[INGESTED] $($sample.Name), ID $($ingested.id), level $($ingested.level)."
        $anomaly = switch ([string]$ingested.anomaly) {
            'True' { 'DETECTED' }
            'False' { 'NOT DETECTED' }
            default { 'UNVERIFIED' }
        }
        Write-Host "[ANOMALY $anomaly] ML status $($ingested.analysisStatus), score $($ingested.decisionScore)."

        if ($ingested.llmAnalysisStatus -eq 'PENDING') {
            Write-Host '[DIAGNOSIS SCHEDULED] Eligible for post-commit processing in memory.'
        }
        $deadline = [DateTime]::UtcNow.AddSeconds($DiagnosisTimeoutSeconds)
        $previous = $null
        do {
            try {
                $persisted = Invoke-RestMethod "$gateway/api/v1/logs/$($ingested.id)" -Headers $readHeaders @requestOptions
            } catch {
                Write-Warning 'Persisted diagnosis status could not be read.'
                $diagnosisVerified = $false
                break
            }

            $status = $persisted.llmAnalysisStatus
            if (-not $status) {
                Write-Host '[DIAGNOSIS NOT SCHEDULED] No diagnosis status is recorded.'
                if ($sample.Name -eq 'Operational error') { $diagnosisVerified = $false }
                break
            }
            if ($status -ne $previous) {
                Write-Host "[DIAGNOSIS $status] Confirmed by persisted log lookup."
                $previous = $status
            }
            if ($status -eq 'COMPLETED') { break }
            if ($status -eq 'FAILED') {
                $diagnosisVerified = $false
                break
            }
            if ([DateTime]::UtcNow -ge $deadline) {
                Write-Warning "Diagnosis still $status at the polling deadline."
                $diagnosisVerified = $false
                break
            }
            Start-Sleep -Seconds 1
        } while ($true)
        Write-Host '[NOTIFICATION DELIVERY UNVERIFIED] External delivery is disabled for this local launch.'
    }
    if (-not $healthy -or -not $diagnosisVerified) { exit 1 }
} finally {
    $lifecycleLock.Dispose()
}
