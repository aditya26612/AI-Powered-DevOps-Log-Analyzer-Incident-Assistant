# Windows local setup

Start the normal local application after checking Java 21, the V9 PostgreSQL database and Ollama.

```powershell
./scripts/start-all.ps1
./scripts/test-local-endpoints.ps1 -SkipAuth
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run lint
./scripts/stop-all.ps1
```

For the browser fixture, restore a protected V9 copy with its own verified data directory on port 15438. Install Python Playwright and use an existing Chrome installation. Use a new guarded Kafka profile so no earlier fixture offsets or records are modified. Keep external notifications disabled.

```powershell
$env:DEVINSIGHT_KAFKA_FIXTURE_NAME = 'fixture-stage09-review'
$env:NOTIFICATION_SLACK_ENABLED = 'false'
$env:NOTIFICATION_EMAIL_ENABLED = 'false'
$env:DIAGNOSIS_ENABLED = 'false'
$env:RAG_SYNCHRONIZE_ON_START = 'false'
./scripts/start-kafka-local.ps1 -Fixture
./scripts/start-all.ps1 -Kafka -KafkaFixture -DatabasePort 15438
./scripts/test-stage08.ps1 -Dashboard -FixtureDataDirectory $verifiedFixtureData -OutputPath $unusedProtectedReceipt
./scripts/stop-all.ps1
./scripts/stop-kafka-local.ps1 -Fixture
```

The dashboard harness needs a fresh Stage 08 notification integration Surefire report for its worker classpath. Stop PostgreSQL only after verifying that port 15438 reports the chosen disposable directory. Keep protected archives, broker storage and database files.
