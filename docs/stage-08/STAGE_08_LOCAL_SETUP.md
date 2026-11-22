# Windows local notification verification

Use Java 21, PostgreSQL 16 with verified Flyway V9, the existing Ollama service and the managed Kafka fixture. Use port 15438 for the Stage 08 fixture. A managed default launch disables Slack and email. Verify that V9 is applied on the disposable database, the target is bound to loopback, and the fixture directory and credentials are protected. Browser verification uses Python Playwright and Chrome.

Normal development with a validated V9 database uses the existing configuration and health checks.

```powershell
java -version
./scripts/start-all.ps1
./scripts/test-local-endpoints.ps1 -SkipAuth
./scripts/stop-all.ps1
```

Run the PostgreSQL and loopback HTTP fixture tests with the non-owner Log Layer role before the fault harness. These commands assume the fixture role was restored with the configured role password.

```powershell
. ./scripts/local-runtime.ps1
$config = Get-DevInsightConfig -Path (Join-Path (Get-Location) '.env.local')
$env:DEVINSIGHT_STAGE08_FIXTURE = 'true'
$env:DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD = $config.LOG_LAYER_DB_PASSWORD
$env:SLACK_WEBHOOK_ENCRYPTION_KEY = $config.SLACK_WEBHOOK_ENCRYPTION_KEY
mvn -f log-layer/pom.xml '-Dtest=NotificationOutboxIntegrationTest,SlackDeliverySafetyTest' '-Dsurefire.reportNameSuffix=stage08fixture' test
Remove-Item Env:DEVINSIGHT_STAGE04_DB_ROLE_PASSWORD,Env:SLACK_WEBHOOK_ENCRYPTION_KEY
```

Use the verified PostgreSQL data directory and an unused output path under a protected directory for the fault run.

```powershell
$env:NOTIFICATION_SLACK_ENABLED = 'false'
$env:NOTIFICATION_EMAIL_ENABLED = 'false'
$env:DIAGNOSIS_ENABLED = 'false'
$env:RAG_SYNCHRONIZE_ON_START = 'false'
./scripts/start-kafka-local.ps1 -Fixture
./scripts/start-all.ps1 -Kafka -KafkaFixture -DatabasePort 15438
./scripts/test-local-endpoints.ps1 -SkipAuth
./scripts/test-stage08.ps1 -FixtureDataDirectory $verifiedFixtureData -OutputPath $unusedReceipt
./scripts/stop-all.ps1
./scripts/stop-kafka-local.ps1 -Fixture
```

The Java test report provides the worker classpath. The fault harness verifies active service ownership and PostgreSQL identity before it interrupts the fixture. Stop PostgreSQL only after comparing its loopback identity and data directory to the verified fixture path. Retain database, broker and backup storage.
