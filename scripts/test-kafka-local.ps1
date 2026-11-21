[CmdletBinding()]
param([switch]$Fixture)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
Initialize-DevInsightKafkaRoot
$profile=Get-DevInsightKafkaProfile -Fixture:$Fixture
if (!(Get-DevInsightOwnedKafkaProcess $profile)) { throw 'No managed Kafka broker is running for this profile' }
$result=Invoke-DevInsightKafkaAdmin $profile status
if (!$result) { throw 'Authenticated topic and offset checks did not pass' }
Write-Output $result
