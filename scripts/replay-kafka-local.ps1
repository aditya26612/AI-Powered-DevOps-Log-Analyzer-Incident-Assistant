[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateRange(0,[long]::MaxValue)][long]$Offset,
    [Parameter(Mandatory)][Guid]$OrganizationId,
    [Parameter(Mandatory)][ValidateRange(1,[long]::MaxValue)][long]$ApplicationId,
    [switch]$Replay,
    [switch]$Fixture
)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
Initialize-DevInsightKafkaRoot
$profile=Get-DevInsightKafkaProfile -Fixture:$Fixture
if (!(Get-DevInsightOwnedKafkaProcess $profile)) { throw 'The selected managed Kafka profile must be running' }
$secret=Read-DevInsightKafkaCredentials $profile
$previousAddress=$env:DEVINSIGHT_BROKER_ADDRESS
$previousSecret=$env:DEVINSIGHT_BROKER_REPLAY_PASSWORD
try {
    $env:DEVINSIGHT_BROKER_ADDRESS=$profile.Address
    $env:DEVINSIGHT_BROKER_REPLAY_PASSWORD=$secret.replay
    $action=if ($Replay) { 'replay' } else { 'preview' }
    & $profile.Java '-cp' "$($profile.Libraries)/*" (Join-Path $PSScriptRoot 'KafkaLocalReplay.java') $action $Offset $OrganizationId $ApplicationId
    if ($LASTEXITCODE -ne 0) { throw 'The selected dead-letter operation did not complete' }
} finally {
    $env:DEVINSIGHT_BROKER_ADDRESS=$previousAddress
    $env:DEVINSIGHT_BROKER_REPLAY_PASSWORD=$previousSecret
}
