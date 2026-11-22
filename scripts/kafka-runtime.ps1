# Shared loopback-only Kafka launcher helpers.
$DevInsightKafkaRoot = Join-Path $env:LOCALAPPDATA 'DevInsight/stage04'
$DevInsightKafkaJava = Join-Path $env:ProgramFiles 'Java/jdk-21/bin/java.exe'

function Initialize-DevInsightKafkaRoot {
    if (!(Test-Path -LiteralPath $DevInsightKafkaRoot)) {
        [IO.Directory]::CreateDirectory($DevInsightKafkaRoot) | Out-Null
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        & icacls.exe $DevInsightKafkaRoot /inheritance:r /grant:r "$($identity.Name):(OI)(CI)(F)" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to protect the Kafka runtime directory' }
    }
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $directorySecurity = if ($PSVersionTable.PSEdition -eq 'Desktop') {
        [IO.Directory]::GetAccessControl($DevInsightKafkaRoot)
    } else {
        [IO.FileSystemAclExtensions]::GetAccessControl([IO.DirectoryInfo]::new($DevInsightKafkaRoot))
    }
    $acl = $directorySecurity.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])
    if (!$acl.Count -or @($acl | Where-Object { $_.IdentityReference.Value -ne $owner }).Count) {
        throw 'Kafka runtime requires a user-restricted directory'
    }
}

function Get-DevInsightKafkaProfile {
    param([switch]$Fixture)
    $name = if (!$Fixture) { 'local' } elseif (!$env:DEVINSIGHT_KAFKA_FIXTURE_NAME) { 'fixture' }
            elseif ($env:DEVINSIGHT_KAFKA_FIXTURE_NAME -cmatch '^fixture-[a-z0-9-]{1,32}$') { $env:DEVINSIGHT_KAFKA_FIXTURE_NAME }
            else { throw 'Invalid disposable Kafka profile name' }
    $directory = Join-Path $DevInsightKafkaRoot $name
    $port = if ($Fixture) { 19094 } else { 19092 }
    $standard = Join-Path $DevInsightKafkaRoot 'kafka_2.13-4.2.2/libs'
    $maven = Join-Path $DevInsightKafkaRoot 'runtime-maven-4.2.2/libs'
    $libs = if (Test-Path -LiteralPath (Join-Path $standard 'kafka_2.13-4.2.2.jar')) { $standard } else { $maven }
    @{
        Directory = $directory
        Data = Join-Path $directory 'data'
        Ports = @($port, ($port + 1))
        Address = "127.0.0.1:$port"
        Credentials = Join-Path $directory 'credentials.private.json'
        Config = Join-Path $directory 'broker.private.properties'
        Process = Join-Path $directory 'broker.process.json'
        ClusterId = Join-Path $directory 'cluster-id'
        Java = $DevInsightKafkaJava
        Libraries = $libs
        BrokerSource = Join-Path $PSScriptRoot 'KafkaLocalBroker.java'
        AdminSource = Join-Path $PSScriptRoot 'KafkaLocalAdmin.java'
    }
}

function New-DevInsightKafkaSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
}

function Read-DevInsightKafkaCredentials {
    param($Profile)
    if (!(Test-Path -LiteralPath $Profile.Credentials)) { throw 'Initialize the selected Kafka profile first' }
    $settings = Get-Content -LiteralPath $Profile.Credentials -Raw | ConvertFrom-Json
    foreach ($name in 'admin','writer','worker','replay') {
        if ($settings.$name -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid protected Kafka profile format' }
    }
    $settings
}

function Get-DevInsightKafkaApplicationEnvironment {
    param([switch]$Fixture,[switch]$PublisherOnly)
    Initialize-DevInsightKafkaRoot
    $profile = Get-DevInsightKafkaProfile -Fixture:$Fixture
    $secret = Read-DevInsightKafkaCredentials $profile
    @{
        DEVINSIGHT_KAFKA_ENABLED = 'true'
        DEVINSIGHT_KAFKA_PRODUCER_ENABLED = 'true'
        DEVINSIGHT_KAFKA_CONSUMER_ENABLED = $(if ($PublisherOnly) { 'false' } else { 'true' })
        DEVINSIGHT_KAFKA_BOOTSTRAP = $profile.Address
        DEVINSIGHT_KAFKA_SECURITY_PROTOCOL = 'SASL_PLAINTEXT'
        DEVINSIGHT_KAFKA_MECHANISM = 'PLAIN'
        DEVINSIGHT_KAFKA_PRODUCER_USERNAME = 'devinsight-writer'
        DEVINSIGHT_KAFKA_PRODUCER_PASSWORD = $secret.writer
        DEVINSIGHT_KAFKA_CONSUMER_USERNAME = $(if (!$PublisherOnly) { 'devinsight-worker' } else { $null })
        DEVINSIGHT_KAFKA_CONSUMER_PASSWORD = $(if (!$PublisherOnly) { $secret.worker } else { $null })
    }
}

function Get-DevInsightOwnedKafkaProcess {
    param($Profile)
    if (!(Test-Path -LiteralPath $Profile.Process)) { return }
    $record = Get-Content -LiteralPath $Profile.Process -Raw | ConvertFrom-Json
    if ($record.RunId -cnotmatch '^[a-f0-9]{32}$') { throw 'Invalid Kafka process record' }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$record.ProcessId)"
    if ($process -and $process.CreationDate.ToUniversalTime().Ticks.ToString() -ceq $record.Ticks -and
        $process.CommandLine -and $process.CommandLine.Contains($Profile.Config) -and
        $process.CommandLine.Contains($Profile.BrokerSource) -and
        $process.CommandLine.Contains("stop-$($record.RunId)")) {
        $process
    }
}

function Invoke-DevInsightKafkaAdmin {
    param($Profile,[ValidateSet('initialize','status')][string]$Operation)
    $secret = Read-DevInsightKafkaCredentials $Profile
    $values = @{
        DEVINSIGHT_BROKER_ADMIN_USER = 'devinsight-admin'
        DEVINSIGHT_BROKER_ADMIN_PASSWORD = $secret.admin
        DEVINSIGHT_BROKER_ADDRESS = $Profile.Address
    }
    $previous = @{}
    try {
        foreach ($name in $values.Keys) {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
            [Environment]::SetEnvironmentVariable($name,$values[$name],'Process')
        }
        $result = & $Profile.Java '-cp' "$($Profile.Libraries)/*" $Profile.AdminSource $Operation 2>> (Join-Path $Profile.Directory 'admin.stderr.log')
        if ($LASTEXITCODE -ne 0) { return }
        $result | Select-Object -Last 1
    } finally {
        foreach ($name in $values.Keys) {
            [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')
        }
    }
}
