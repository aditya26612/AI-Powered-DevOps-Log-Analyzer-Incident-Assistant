# ==============================================================================
# DevInsight - One-Click Shutdown Script (PowerShell)
# ==============================================================================
Write-Host "Stopping all DevInsight services..." -ForegroundColor Cyan

$ports = @(5173, 8080, 8081, 8082, 8084, 8000)

foreach ($port in $ports) {
    try {
        $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
        if ($connections) {
            $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
            foreach ($processId in $pids) {
                if ($processId -gt 0) {
                    Write-Host "  -> Terminating process on port $port (PID: $processId)..." -ForegroundColor Yellow
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                }
            }
        }
    } catch {
        # ignore errors if already closed
    }
}

Write-Host "All DevInsight processes terminated." -ForegroundColor Green

