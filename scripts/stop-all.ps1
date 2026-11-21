[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-runtime.ps1')

$lock = Open-DevInsightLifecycleLock
try {
    $failed = $false
    $found = $false
    foreach ($record in Get-DevInsightRecords) {
        $root = Get-DevInsightManagedProcess $record
        if (-not $root) { continue }
        $found = $true
        Write-Host "Stopping $($record.Service) (managed PID $($record.ProcessId))..."
        $processes = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CreationDate)
        $tree = @($root) + @(Get-DevInsightProcessTree -RootProcess $root -Processes $processes)

        if (Get-DevInsightManagedProcess $record) {
            taskkill.exe /PID $record.ProcessId /T /F | Out-Host
            $killStatus = $LASTEXITCODE
            if ($killStatus -ne 0) {
                Write-Warning "Stopping $($record.Service) returned taskkill exit code $killStatus."
                $failed = $true
            }
        }

        $deadline = [DateTime]::UtcNow.AddSeconds(10)
        do {
            $current = @(Get-CimInstance Win32_Process -Property ProcessId, CreationDate)
            $survivors = @($tree | Where-Object {
                $prior = $_
                @($current | Where-Object { $_.ProcessId -eq $prior.ProcessId -and $_.CreationDate -eq $prior.CreationDate }).Count -gt 0
            })
            if ($survivors.Count) { Start-Sleep -Milliseconds 300 }
        } while ($survivors.Count -and [DateTime]::UtcNow -lt $deadline)

        if ($survivors.Count) {
            Write-Warning "$($record.Service) still has $($survivors.Count) managed processes running."
            $failed = $true
        } else {
            Write-Host "[STOPPED] $($record.Service)"
        }
    }
    if ($failed) { exit 1 }
    if (-not $found) { Write-Host 'No managed DevInsight processes are running.' }
} finally {
    $lock.Dispose()
}
