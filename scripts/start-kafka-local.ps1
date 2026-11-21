[CmdletBinding()]
param([switch]$Fixture,[ValidateRange(10,180)][int]$TimeoutSeconds=70)

$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kafka-runtime.ps1')
Initialize-DevInsightKafkaRoot
$profile=Get-DevInsightKafkaProfile -Fixture:$Fixture
if (!(Test-Path -LiteralPath $profile.Java) -or !(Test-Path -LiteralPath (Join-Path $profile.Libraries 'kafka_2.13-4.2.2.jar'))) {
    throw 'Kafka 4.2.2 or Java 21 is missing. Run scripts/setup-kafka-local.ps1.'
}
[IO.Directory]::CreateDirectory($profile.Directory) | Out-Null
$lock=[IO.File]::Open((Join-Path $profile.Directory 'operation.lock'),'OpenOrCreate','ReadWrite','None')
try {
    if (Get-DevInsightOwnedKafkaProcess $profile) {
        $result=Invoke-DevInsightKafkaAdmin $profile status
        if (!$result) { throw 'The managed Kafka broker is running but readiness could not be verified' }
        Write-Output $result
        return
    }
    if (Get-NetTCPConnection -State Listen -LocalPort $profile.Ports -ErrorAction SilentlyContinue) {
        throw 'A selected Kafka port is occupied. No unmanaged process was stopped.'
    }
    $metadata=Join-Path $profile.Data 'meta.properties'
    if (!(Test-Path -LiteralPath $profile.Credentials)) {
        if ((Test-Path -LiteralPath $profile.Config) -or (Test-Path -LiteralPath $metadata)) {
            throw 'Kafka storage or configuration exists without credentials. Restore its protected settings.'
        }
        $secrets=@{}
        foreach ($name in 'admin','writer','worker','replay') { $secrets[$name]=New-DevInsightKafkaSecret }
        [IO.File]::WriteAllText($profile.Credentials,($secrets | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    }
    $secret=Read-DevInsightKafkaCredentials $profile
    if (!(Test-Path -LiteralPath $profile.Config)) {
        if (Test-Path -LiteralPath $metadata) { throw 'Existing Kafka storage requires its original broker configuration' }
        $data=$profile.Data.Replace('\','/')
        $controller="127.0.0.1:$($profile.Ports[1])"
        $conf=@"
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@$controller
controller.listener.names=CONTROLLER
listeners=CLIENT://$($profile.Address),CONTROLLER://$controller
advertised.listeners=CLIENT://$($profile.Address)
listener.security.protocol.map=CLIENT:SASL_PLAINTEXT,CONTROLLER:SASL_PLAINTEXT
inter.broker.listener.name=CLIENT
sasl.enabled.mechanisms=PLAIN
sasl.mechanism.inter.broker.protocol=PLAIN
sasl.mechanism.controller.protocol=PLAIN
listener.name.client.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="devinsight-admin" password="$($secret.admin)" user_devinsight-admin="$($secret.admin)" user_devinsight-writer="$($secret.writer)" user_devinsight-worker="$($secret.worker)" user_devinsight-replay="$($secret.replay)";
listener.name.controller.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="devinsight-admin" password="$($secret.admin)" user_devinsight-admin="$($secret.admin)";
authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer
super.users=User:devinsight-admin
allow.everyone.if.no.acl.found=false
log.dirs=$data
auto.create.topics.enable=false
delete.topic.enable=false
offsets.topic.num.partitions=3
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
num.partitions=3
message.max.bytes=131072
num.network.threads=2
num.io.threads=3
log.flush.interval.messages=1
"@
        [IO.File]::WriteAllText($profile.Config,$conf,[Text.UTF8Encoding]::new($false))
    }
    if (!(Test-Path -LiteralPath $metadata)) {
        if ((Test-Path -LiteralPath $profile.Data) -and @(Get-ChildItem -LiteralPath $profile.Data -Force).Count) {
            throw 'Kafka storage is populated without metadata. Existing files were preserved.'
        }
        if (!(Test-Path -LiteralPath $profile.ClusterId)) {
            $id = & $profile.Java '-cp' "$($profile.Libraries)/*" kafka.tools.StorageTool random-uuid
            if ($LASTEXITCODE -ne 0 -or $id -cnotmatch '^[A-Za-z0-9_-]{22}$') { throw 'Kafka cluster ID generation failed' }
            [IO.File]::WriteAllText($profile.ClusterId,$id,[Text.UTF8Encoding]::new($false))
        }
        $id = [IO.File]::ReadAllText($profile.ClusterId)
        if ($id -cnotmatch '^[A-Za-z0-9_-]{22}$') { throw 'Invalid protected Kafka cluster ID' }
        & $profile.Java '-cp' "$($profile.Libraries)/*" kafka.tools.StorageTool format -t $id -c $profile.Config *> (Join-Path $profile.Directory 'format.log')
        if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $metadata)) { throw 'Kafka KRaft format failed. See the protected format diagnostic.' }
    }

    $run=[Guid]::NewGuid().ToString('N')
    $signal=Join-Path $profile.Directory "stop-$run"
    $argsText='-Xms128m -Xmx512m -cp "{0}/*" "{1}" "{2}" "{3}"' -f $profile.Libraries,$profile.BrokerSource,$profile.Config,$signal
    $launch=@{
        FilePath=$profile.Java; ArgumentList=$argsText; WindowStyle='Hidden'; PassThru=$true
        WorkingDirectory=$profile.Directory
        RedirectStandardOutput=Join-Path $profile.Directory "broker-$run.stdout"
        RedirectStandardError=Join-Path $profile.Directory "broker-$run.stderr"
    }
    $broker=Start-Process @launch
    $process=Get-CimInstance Win32_Process -Filter "ProcessId = $($broker.Id)"
    if (!$process) { throw 'Kafka broker exited during startup. See the protected broker diagnostic.' }
    $record=@{ RunId=$run; ProcessId=$broker.Id; Ticks=$process.CreationDate.ToUniversalTime().Ticks.ToString() }
    [IO.File]::WriteAllText($profile.Process,($record | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $broker.Refresh()
        if ($broker.HasExited) { throw 'Kafka broker exited before readiness. See the protected broker diagnostic.' }
        $result=Invoke-DevInsightKafkaAdmin $profile initialize
        if ($result) {
            Write-Output $result
            return
        }
        Start-Sleep -Milliseconds 700
    } while ([DateTime]::UtcNow -lt $deadline)
    [IO.File]::WriteAllText($signal,'shutdown')
    throw 'Kafka did not pass authenticated readiness before the deadline. Shutdown requested.'
} finally {
    $lock.Dispose()
}
