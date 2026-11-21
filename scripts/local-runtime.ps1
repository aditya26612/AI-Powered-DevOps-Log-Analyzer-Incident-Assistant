# Shared Windows local launcher and probe utilities.
Add-Type -AssemblyName System.Net.Http
$DevInsightRuntimeDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'logs/local-services'
$DevInsightWorker = Join-Path $PSScriptRoot 'run-local-service.ps1'

function Get-DevInsightServices {
    @(
        @{ Id = 'ml-pipeline'; Name = 'ML Pipeline'; Port = 8000; Path = '/api/v1/system/health'; Kind = 'Model' },
        @{ Id = 'user-service'; Name = 'User Service'; Port = 8081; Path = '/actuator/health'; Kind = 'Health' },
        @{ Id = 'log-layer'; Name = 'Log Layer'; Port = 8082; Path = '/actuator/health'; Kind = 'Health' },
        @{ Id = 'llm-service'; Name = 'LLM Service'; Port = 8084; Path = '/actuator/health'; Kind = 'Health' },
        @{ Id = 'api-gateway'; Name = 'API Gateway'; Port = 8080; Path = '/actuator/health'; Kind = 'Health' },
        @{ Id = 'frontend'; Name = 'Frontend'; Port = 5173; Path = '/'; Kind = 'Frontend' }
    )
}

function Get-DevInsightConfig {
    param([string]$Path)
    $config = @{}
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in Get-Content -LiteralPath $Path) {
            if ($line -match '^\s*([A-Za-z_][A-Za-z_0-9]*)\s*=(.*)$') {
                $config[$Matches[1]] = $Matches[2].Trim()
            }
        }
    }
    foreach ($name in 'DB_PASSWORD', 'JWT_SECRET', 'INTERNAL_API_KEY', 'SLACK_WEBHOOK_ENCRYPTION_KEY', 'LOG_LAYER_DB_USERNAME', 'LOG_LAYER_DB_PASSWORD') {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value) { $config[$name] = $value }
    }
    return $config
}

function Open-DevInsightLifecycleLock {
    [IO.Directory]::CreateDirectory($DevInsightRuntimeDir) | Out-Null
    $path = Join-Path $DevInsightRuntimeDir 'operation.lock'
    try {
        [IO.File]::Open($path, 'OpenOrCreate', 'ReadWrite', 'None')
    } catch [IO.IOException] {
        throw 'Cannot acquire the DevInsight lifecycle lock. Check whether another start or stop is in progress.'
    }
}

function Get-DevInsightManagedProcess {
    param($Record)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$Record.ProcessId)" -ErrorAction Stop
    if ($process -and
        $process.CreationDate.ToUniversalTime().Ticks.ToString() -ceq $Record.StartTicks -and
        $process.CommandLine -and
        $process.CommandLine.IndexOf($DevInsightWorker, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $process.CommandLine.TrimEnd() -match (' -Service ' + [regex]::Escape($Record.Service) + ' -RunId ' + [regex]::Escape($Record.RunId) + '$')) {
        return $process
    }
}

function Get-DevInsightRecords {
    if (Test-Path -LiteralPath $DevInsightRuntimeDir) {
        foreach ($file in Get-ChildItem -LiteralPath $DevInsightRuntimeDir -Recurse -File -Filter '*.process.json') {
            try {
                $record = Get-Content -Raw -LiteralPath $file.FullName | ConvertFrom-Json
                if (-not $record.ProcessId -or -not $record.StartTicks -or $record.RunId -notmatch '^[a-f0-9]{32}$' -or
                    $record.RunId -ne $file.Directory.Name -or $file.Name -ne "$($record.Service).process.json") {
                    throw 'Unexpected process record format'
                }
                $record
            } catch {
                Write-Warning "Could not read the process record at $($file.FullName)."
            }
        }
    }
}

function Get-DevInsightProcessTree {
    param($RootProcess, [array]$Processes)
    foreach ($child in $Processes) {
        if ($child.ParentProcessId -eq $RootProcess.ProcessId -and $child.CreationDate -ge $RootProcess.CreationDate) {
            $child
            Get-DevInsightProcessTree -RootProcess $child -Processes $Processes
        }
    }
}

function Test-DevInsightListener {
    param($RootProcess, [int]$Port, [array]$Connections, [array]$Processes)
    $owners = @($Connections | Where-Object LocalPort -eq $Port | Select-Object -ExpandProperty OwningProcess)
    if ($owners.Count -eq 0) { return $false }
    $tree = @($RootProcess) + @(Get-DevInsightProcessTree -RootProcess $RootProcess -Processes $Processes)
    return @($owners | Where-Object { $_ -notin $tree.ProcessId }).Count -eq 0
}

function Invoke-DevInsightProbe {
    param([uri]$Uri, [string]$Kind, [int]$TimeoutSeconds = 3)
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseProxy = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    try {
        $response = $client.GetAsync($Uri).GetAwaiter().GetResult()
        try {
            $code = [int]$response.StatusCode
            if ($code -ne 200) {
                $label = switch ($code) {
                    { $_ -in 401, 403 } { 'AUTH REQUIRED'; break }
                    404 { 'ENDPOINT NOT FOUND'; break }
                    503 { 'SERVICE UNAVAILABLE'; break }
                    default { 'HTTP ERROR' }
                }
                return @{ Ready = $false; Summary = "$label (HTTP $code)" }
            }
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ($Kind -eq 'Frontend') {
                $ready = $response.Content.Headers.ContentType.MediaType -eq 'text/html' -and $body -match '<div\s+id="root"'
            } else {
                $health = $body | ConvertFrom-Json -ErrorAction Stop
                $ready = $health.status -eq 'UP' -and ($Kind -ne 'Model' -or $health.model_loaded -eq $true)
            }
            if ($ready) { return @{ Ready = $true; Summary = 'READY (HTTP 200)' } }
            return @{ Ready = $false; Summary = 'NOT READY (HTTP 200)' }
        } finally {
            $response.Dispose()
        }
    } catch {
        $cause = $_.Exception
        while ($cause) {
            if ($cause -is [OperationCanceledException] -or
                ($cause -is [Net.Sockets.SocketException] -and $cause.SocketErrorCode -eq 'TimedOut')) {
                return @{ Ready = $false; Summary = 'TIMEOUT' }
            }
            if ($cause -is [Net.Sockets.SocketException] -and $cause.SocketErrorCode -eq 'ConnectionRefused') {
                return @{ Ready = $false; Summary = 'CONNECTION REFUSED' }
            }
            $cause = $cause.InnerException
        }
        if ($response -and [int]$response.StatusCode -eq 200) {
            return @{ Ready = $false; Summary = 'INVALID HEALTH RESPONSE (HTTP 200)' }
        }
        return @{ Ready = $false; Summary = 'CONNECTION ERROR' }
    } finally {
        $client.Dispose()
    }
}
