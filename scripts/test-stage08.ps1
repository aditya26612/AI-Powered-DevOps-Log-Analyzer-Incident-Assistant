[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputPath,
      [Parameter(Mandatory)][string]$FixtureDataDirectory,
      [switch]$Dashboard,
      [switch]$Benchmark,
      [switch]$Faults)

$ErrorActionPreference = 'Stop'
if (@($Dashboard, $Benchmark, $Faults).Where({ $_ }).Count -gt 1) { throw 'Select one fixture workload.' }
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
$root = Split-Path -Parent $PSScriptRoot
$services = Get-DevInsightServices
$active = @(Get-DevInsightRecords | Where-Object { Get-DevInsightManagedProcess $_ })
if ($active.Count -ne 6 -or @($active | Where-Object { $_.DatabasePort -ne 15438 -or !$_.KafkaFixture }).Count -ne 0 -or
    @($active.RunId | Select-Object -Unique).Count -ne 1 -or !(Get-DevInsightOwnedKafkaProcess (Get-DevInsightKafkaProfile -Fixture))) {
    throw 'Start the six managed services against the Kafka fixture and isolated PostgreSQL port 15438.'
}
$processes = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CreationDate)
$connections = @(Get-NetTCPConnection -State Listen)
foreach ($service in $services) {
    $record = $active | Where-Object Service -eq $service.Id
    if (!(Test-DevInsightListener (Get-DevInsightManagedProcess $record) $service.Port $connections $processes)) {
        throw "The $($service.Name) listener is outside the verified fixture process tree."
    }
}
if ($env:NOTIFICATION_SLACK_ENABLED -ne 'false' -or $env:NOTIFICATION_EMAIL_ENABLED -ne 'false') {
    throw 'Keep Slack and email disabled during verification.'
}
$cfg = Get-DevInsightConfig -Path (Join-Path $root '.env.local')
$options = @{
    STAGE08_MANAGED = '15438'
    STAGE08_ADMIN_PASSWORD = $cfg.DB_PASSWORD
    STAGE08_RUNTIME_DB_USER = $cfg.LOG_LAYER_DB_USERNAME
    STAGE08_RUNTIME_DB_PASSWORD = $cfg.LOG_LAYER_DB_PASSWORD
    STAGE08_INTERNAL_KEY = $cfg.INTERNAL_API_KEY
    STAGE08_JWT = $cfg.JWT_SECRET
    STAGE08_WEBHOOK_KEY = $cfg.SLACK_WEBHOOK_ENCRYPTION_KEY
    STAGE08_FIXTURE_PGDATA = (Resolve-Path -LiteralPath $FixtureDataDirectory).Path
}
$previous = @{}
$lock = [System.Threading.Mutex]::new($false, 'DevInsight.Stage08.FixtureScenario')
$ownsLock = $false
try {
    $ownsLock = $lock.WaitOne(0)
    if (!$ownsLock) { throw 'Run one Stage 08 fixture scenario at a time.' }
    foreach ($key in $options.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key)
        [Environment]::SetEnvironmentVariable($key, $options[$key])
    }
    $harness = if ($Faults) { 'stage10-faults.py' }
               elseif ($Benchmark) { 'stage10-benchmark.py' }
               elseif ($Dashboard) { 'stage09-dashboard-demo.py' }
               else { 'stage08-notification-demo.py' }
    python (Join-Path $PSScriptRoot $harness) $OutputPath
    if ($LASTEXITCODE -ne 0) { throw 'Disposable workflow verification failed.' }
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key]) }
    if ($ownsLock) { $lock.ReleaseMutex() }
    $lock.Dispose()
}
