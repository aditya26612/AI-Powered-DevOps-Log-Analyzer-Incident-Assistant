[CmdletBinding()]
param(
    [ValidateRange(1, 900)][int]$ReadinessTimeoutSeconds = 180,
    [ValidateRange(1, 65535)][int]$DatabasePort = 5432,
    [switch]$Kafka,
    [switch]$KafkaFixture,
    [switch]$KafkaPublisherOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-runtime.ps1')

if (($KafkaFixture -or $KafkaPublisherOnly) -and !$Kafka) {
    throw 'KafkaFixture and KafkaPublisherOnly require -Kafka.'
}
if ($KafkaFixture -and $DatabasePort -notin 55438,15438) {
    throw 'The Kafka fixture requires an isolated PostgreSQL port (55438 or 15438).'
}
$kafkaSettings = @{}
if ($Kafka) {
    . (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
    $kafkaSettings = Get-DevInsightKafkaApplicationEnvironment -Fixture:$KafkaFixture -PublisherOnly:$KafkaPublisherOnly
}
$protectedKafkaVariables = @('DEVINSIGHT_KAFKA_ENABLED','DEVINSIGHT_KAFKA_PRODUCER_USERNAME',
    'DEVINSIGHT_KAFKA_PRODUCER_PASSWORD','DEVINSIGHT_KAFKA_CONSUMER_USERNAME','DEVINSIGHT_KAFKA_CONSUMER_PASSWORD')

$root = Split-Path -Parent $PSScriptRoot
$config = Get-DevInsightConfig -Path (Join-Path $root '.env.local')
foreach ($name in 'DB_PASSWORD', 'JWT_SECRET', 'INTERNAL_API_KEY') {
    if (-not $config[$name]) { throw "$name is missing. Use scripts/setup-local-env.ps1 for initial setup." }
}
if ([Text.Encoding]::UTF8.GetByteCount($config.JWT_SECRET) -lt 32 -or $config.INTERNAL_API_KEY.Length -lt 16) {
    throw 'Credential length validation failed. JWT_SECRET requires 32 UTF-8 bytes and INTERNAL_API_KEY requires 16 characters.'
}
Write-Host 'Persistent local credentials configured. Launching with ACTIVE_MODEL=v2.'

$services = @(Get-DevInsightServices)
$records = @{}
$databaseNames = @{ 'user-service' = 'devinsight'; 'log-layer' = 'log_layer_db'; 'llm-service' = 'llm_service_db' }
$lock = Open-DevInsightLifecycleLock
try {
    foreach ($record in Get-DevInsightRecords) {
        if (Get-DevInsightManagedProcess $record) { throw 'DevInsight is already running. Use scripts/stop-all.ps1 before restarting.' }
    }
    $occupied = @(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in $services.Port })
    if ($occupied.Count) {
        throw "DevInsight service ports already in use: $(($occupied.LocalPort | Sort-Object -Unique) -join ', ')."
    }

    $runId = [Guid]::NewGuid().ToString('N')
    $runDirectory = Join-Path $DevInsightRuntimeDir $runId
    [IO.Directory]::CreateDirectory($runDirectory) | Out-Null
    Write-Host "Service logs: $runDirectory"

    foreach ($service in $services) {
        $workerEnvironment = @{
            DB_PASSWORD = $null
            JWT_SECRET = $null
            INTERNAL_API_KEY = $null
            SLACK_WEBHOOK_ENCRYPTION_KEY = $null
            LOG_LAYER_DB_USERNAME = $null
            LOG_LAYER_DB_PASSWORD = $null
            SERVER_ADDRESS = '127.0.0.1'
        }
        foreach ($name in $protectedKafkaVariables) { $workerEnvironment[$name] = $null }
        if ($service.Id -eq 'log-layer') {
            $workerEnvironment.NOTIFICATION_DELIVERY_TRANSPORT = 'OFF'
            foreach ($name in $kafkaSettings.Keys) { $workerEnvironment[$name] = $kafkaSettings[$name] }
        }
        if ($databaseNames.ContainsKey($service.Id)) {
            $workerEnvironment.DB_PASSWORD = $config.DB_PASSWORD
            if ($PSBoundParameters.ContainsKey('DatabasePort')) {
                $workerEnvironment.SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$DatabasePort/$($databaseNames[$service.Id])"
            }
        }
        if ($service.Id -in 'api-gateway', 'user-service', 'log-layer') {
            $workerEnvironment.JWT_SECRET = $config.JWT_SECRET
        }
        if ($service.Id -in 'api-gateway', 'user-service', 'log-layer', 'ml-pipeline', 'llm-service') {
            $workerEnvironment.INTERNAL_API_KEY = $config.INTERNAL_API_KEY
        }
        if ($service.Id -eq 'log-layer') {
            $workerEnvironment.SLACK_WEBHOOK_ENCRYPTION_KEY = $config.SLACK_WEBHOOK_ENCRYPTION_KEY
            $workerEnvironment.LOG_LAYER_DB_USERNAME = $config.LOG_LAYER_DB_USERNAME
            $workerEnvironment.LOG_LAYER_DB_PASSWORD = $config.LOG_LAYER_DB_PASSWORD
        }
        if ($service.Id -eq 'ml-pipeline') { $workerEnvironment.ACTIVE_MODEL = 'v2' }

        # Hidden workers use their own console. A redirected launcher can exit
        # while the workers continue writing to their own log files.
        $powershellPath = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
        $arguments = '-NoProfile -NonInteractive -File "{0}" -Service {1} -RunId {2}' -f $DevInsightWorker, $service.Id, $runId
        $previousEnvironment = @{}
        try {
            foreach ($name in $workerEnvironment.Keys) {
                $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
                [Environment]::SetEnvironmentVariable($name, $workerEnvironment[$name], 'Process')
            }
            $child = Start-Process -FilePath $powershellPath -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru
        } finally {
            foreach ($name in $previousEnvironment.Keys) {
                [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
            }
        }
        try {
            $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($child.Id)"
            if (-not $process) { throw "$($service.Name) exited before it could be recorded. Check its service log." }
            $record = @{
                RunId = $runId
                DatabasePort = $DatabasePort
                KafkaFixture = [bool]$KafkaFixture
                Service = $service.Id
                ProcessId = $child.Id
                StartTicks = $process.CreationDate.ToUniversalTime().Ticks.ToString()
            }
            $path = Join-Path $runDirectory "$($service.Id).process.json"
            $record | ConvertTo-Json -Compress | Set-Content -LiteralPath "$path.tmp" -Encoding UTF8
            [IO.File]::Move("$path.tmp", $path)
            $records[$service.Id] = $record
            Write-Host "[LAUNCHED] $($service.Name)"
        } catch {
            if (-not $child.HasExited) { taskkill.exe /PID $child.Id /T /F | Out-Host }
            throw
        } finally {
            $child.Dispose()
        }
    }
} finally {
    $lock.Dispose()
}

$remaining = @($services)
$results = @{}
$deadline = [DateTime]::UtcNow.AddSeconds($ReadinessTimeoutSeconds)
while ($remaining.Count -and [DateTime]::UtcNow -lt $deadline) {
    $connections = @(Get-NetTCPConnection -State Listen)
    $processes = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CreationDate)
    foreach ($service in $remaining) {
        if ([DateTime]::UtcNow -ge $deadline) { break }
        $managed = Get-DevInsightManagedProcess $records[$service.Id]
        if (-not $managed) {
            $results[$service.Id] = @{ Ready = $false; Summary = 'PROCESS EXITED' }
            continue
        }
        $uri = 'http://127.0.0.1:{0}{1}' -f $service.Port, $service.Path
        $probe = Invoke-DevInsightProbe -Uri $uri -Kind $service.Kind -TimeoutSeconds 2
        if ($probe.Ready -and -not (Test-DevInsightListener $managed $service.Port $connections $processes)) {
            $probe = @{ Ready = $false; Summary = 'LISTENER OWNERSHIP UNVERIFIED' }
        }
        $results[$service.Id] = $probe
        if ($probe.Ready) { Write-Host "[$($probe.Summary)] $($service.Name)" }
    }
    $remaining = @($remaining | Where-Object { -not $results[$_.Id].Ready })
    if ($remaining.Count -and $remaining.Count -eq @($remaining | Where-Object { $results[$_.Id].Summary -eq 'PROCESS EXITED' }).Count) { break }
    if ($remaining.Count) { Start-Sleep -Seconds 1 }
}

if ($remaining.Count) {
    foreach ($service in $remaining) {
        $summary = $results[$service.Id].Summary
        if (-not $summary) { $summary = 'READINESS CHECK NOT REACHED' }
        Write-Warning "$($service.Name): $summary"
    }
    Write-Error "DevInsight did not reach readiness. See $runDirectory and use scripts/stop-all.ps1 to stop this launch."
    exit 1
}
Write-Host 'All six DevInsight services verified ready.'
