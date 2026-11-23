[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('ml-pipeline','api-gateway')][string]$Service)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
$lock = Open-DevInsightLifecycleLock
try {
    if ($env:DEVINSIGHT_KAFKA_FIXTURE_NAME -cnotmatch '^fixture-stage10-[a-z0-9-]+$' -or
        !(Get-DevInsightOwnedKafkaProcess (Get-DevInsightKafkaProfile -Fixture))) {
        throw 'A managed Stage 10 Kafka fixture is required.'
    }
    $active = @(Get-DevInsightRecords | Where-Object { Get-DevInsightManagedProcess $_ })
    if ($active.Count -ne 6 -or @($active | Where-Object { $_.DatabasePort -ne 15438 -or !$_.KafkaFixture }).Count -ne 0 -or
        @($active.RunId | Select-Object -Unique).Count -ne 1) {
        throw 'Expected one managed six-service Stage 10 fixture.'
    }
    $record = $active | Where-Object Service -eq $Service
    $process = Get-DevInsightManagedProcess $record
    $port = (Get-DevInsightServices | Where-Object Id -eq $Service).Port
    $processes = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CreationDate)
    if (!$process -or !(Test-DevInsightListener $process $port @(Get-NetTCPConnection -State Listen) $processes)) {
        throw 'Selected listener is outside the eligible fixture process tree.'
    }
    taskkill.exe /PID $record.ProcessId /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The verified fixture process did not stop.' }
} finally {
    $lock.Dispose()
}
