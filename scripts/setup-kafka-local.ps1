[CmdletBinding()]
param()

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
Initialize-DevInsightKafkaRoot
$profile=Get-DevInsightKafkaProfile
if (!(Test-Path -LiteralPath $profile.Java)) { throw 'Java 21 is required at the local Java 21 installation path' }
if ((Get-PSDrive ([IO.Path]::GetPathRoot($DevInsightKafkaRoot).Trim(':','\'))).Free -lt 1GB) {
    throw 'At least 1 GiB free space is required in the local development runtime'
}
$base=Join-Path $DevInsightKafkaRoot 'runtime-maven-4.2.2'
[IO.Directory]::CreateDirectory($base) | Out-Null
$pom=Join-Path (Split-Path -Parent $PSScriptRoot) 'infra/kafka/pom.xml'
& mvn.cmd -B -C -f $pom dependency:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$(Join-Path $base 'libs')" *> (Join-Path $base 'resolve.log')
if ($LASTEXITCODE -ne 0) { throw 'Kafka 4.2.2 dependencies could not be resolved. See the protected setup diagnostic.' }
Write-Output 'Apache Kafka 4.2.2 runtime dependencies are available in protected user storage.'
