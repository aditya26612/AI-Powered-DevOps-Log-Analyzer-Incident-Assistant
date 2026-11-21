[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('ml-pipeline', 'user-service', 'log-layer', 'llm-service', 'api-gateway', 'frontend')]
    [string]$Service,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{32}$')][string]$RunId
)

$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root "logs/local-services/$RunId/$Service.log"
& {
    switch ($Service) {
        'ml-pipeline' {
            Set-Location -LiteralPath (Join-Path $root 'ml-pipeline')
            python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
        }
        'frontend' {
            Set-Location -LiteralPath (Join-Path $root 'frontend')
            npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
        }
        'log-layer' {
            mvn.cmd '-Dmaven.test.skip=true' -f log-layer/pom.xml '-Dspring-boot.run.arguments=--notification.slack.enabled=false --notification.email.enabled=false' spring-boot:run
        }
        default {
            mvn.cmd '-Dmaven.test.skip=true' -f "$Service/pom.xml" spring-boot:run
        }
    }
} *> $log
exit $LASTEXITCODE
