[CmdletBinding()]
param([switch]$Fixture)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
Initialize-DevInsightKafkaRoot
$profile=Get-DevInsightKafkaProfile -Fixture:$Fixture
if (!(Test-Path -LiteralPath $profile.Directory)) { return }
$lock=[IO.File]::Open((Join-Path $profile.Directory 'operation.lock'),'OpenOrCreate','ReadWrite','None')
try {
    $broker=Get-DevInsightOwnedKafkaProcess $profile
    if (!$broker) { Write-Output 'No managed Kafka broker is running'; return }
    $record=Get-Content -LiteralPath $profile.Process -Raw | ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $profile.Directory "stop-$($record.RunId)"),'shutdown')
    $deadline=[DateTime]::UtcNow.AddSeconds(25)
    while (Get-DevInsightOwnedKafkaProcess $profile) {
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Kafka did not complete graceful shutdown before the deadline' }
        Start-Sleep -Milliseconds 250
    }
    Write-Output 'Managed Kafka stopped. Persistent storage retained.'
} finally {
    $lock.Dispose()
}
