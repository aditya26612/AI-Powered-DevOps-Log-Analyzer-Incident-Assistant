# Windows local setup and troubleshooting

## Prepare the application

For native development, use JDK 21, Maven, Python 3.11, a Node version accepted by the frontend lockfile, PostgreSQL with pgvector, and Ollama. The repository already includes the V1 and V2 model artifacts under `ml-pipeline/artifacts/`. See the [ML pipeline guide](../ml-pipeline/README.md) for model selection and verification.

The observability workspace uses the V9 schema. See [Local verification and benchmarks](VERIFICATION.md#reliability-and-reproduction) for the isolated fixture requirements and verification workflow.

For Compose and Kubernetes, follow the [container runbook](../system-architecture/CONTAINER_DEPLOYMENT.md) for private configuration, database provisioning, image selection, and readiness checks. Use `docker compose config --quiet` to validate Compose configuration without printing interpolated values.

The verified Compose path requires Ollama running on the Docker Desktop host with `phi3:mini` and `nomic-embed-text:latest` already downloaded. The [host Ollama override](../system-architecture/CONTAINER_DEPLOYMENT.md#use-ollama-already-running-on-the-docker-desktop-host) connects the LLM container to that server.

| Service | Default local port | Readiness check |
|---|---:|---|
| Gateway | 8080 | `GET /actuator/health` |
| User Service | 8081 | `GET /actuator/health` |
| Log Layer | 8082 | `GET /actuator/health` |
| FastAPI | 8000 | `GET /api/v1/system/health` and `/api/v1/system/model` |
| LLM Service | 8084 | `GET /actuator/health` |
| Ollama | 11434 | `ollama list` and `GET /api/tags` |
| PostgreSQL | 5432 | `pg_isready` and the expected logical databases |
| Vite | 5173 | Open the UI and sign in through the Gateway |

## Controlled live validation

**Execution status in the Work session: NOT EXECUTED.** Windows loopback access was blocked. The following commands target a disposable PostgreSQL container and loopback-only services. They write three synthetic logs and request one LLM diagnosis. Execute from the repository root in PowerShell with Docker and Ollama available. Use the same disposable credentials in the service terminals, and leave any other application processes running on their own ports.

### 1. Start disposable PostgreSQL and check Ollama

Choose a new container name if the name below already exists. Using `--rm` removes this test container and its writable layer when it stops.

```powershell
$env:DB_PASSWORD = '<choose a fresh disposable password>'
docker run -d --rm --name devinsight-validation-db -p 127.0.0.1:55432:5432 -e "POSTGRES_PASSWORD=$env:DB_PASSWORD" pgvector/pgvector:pg16
docker exec devinsight-validation-db pg_isready -U postgres
Get-Content docker/init-db.sql -Raw | docker exec -i -e "PGPASSWORD=$env:DB_PASSWORD" devinsight-validation-db psql -U postgres -d postgres -v ON_ERROR_STOP=1
ollama list
```

Wait until PostgreSQL accepts connections before applying the SQL once. Start `ollama serve` in its own terminal if needed. If either model is absent, use `ollama pull phi3:mini` and `ollama pull nomic-embed-text` before starting the LLM service.

### 2. Start the three services in separate terminals

FastAPI:

```powershell
Set-Location ml-pipeline
python -m pip install -r requirements.txt
$env:ACTIVE_MODEL = 'v2'
$env:INTERNAL_API_KEY = '<choose a fresh disposable ML key>'
Remove-Item Env:MODEL_PATH,Env:ANOMALY_THRESHOLD -ErrorAction SilentlyContinue
python -m uvicorn app.main:app --host 127.0.0.1 --port 58000
```

LLM Service:

```powershell
$env:DB_PASSWORD = '<same disposable database password>'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55432/llm_service_db'
$env:OLLAMA_BASE_URL = 'http://127.0.0.1:11434'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '58084'
mvn -f llm-service/pom.xml spring-boot:run
```

Log Layer:

```powershell
$env:DB_PASSWORD = '<same disposable database password>'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55432/log_layer_db'
$env:INTERNAL_API_KEY = '<same disposable ML key>'
$env:ML_SERVICE_URL = 'http://127.0.0.1:58000'
$env:LLM_SERVICE_URL = 'http://127.0.0.1:58084'
$env:NOTIFICATION_SLACK_ENABLED = 'false'
$env:NOTIFICATION_EMAIL_ENABLED = 'false'
$env:NOTIFICATION_AUTO_DIAGNOSE = 'true'
Remove-Item Env:NOTIFICATION_SLACK_WEBHOOK_URL,Env:NOTIFICATION_EMAIL_TO -ErrorAction SilentlyContinue
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '58082'
mvn -f log-layer/pom.xml spring-boot:run
```

Use a fresh database with no per-application integrations. The disposable Log Layer creates its local schema through Hibernate. The validation requests use the direct, loopback-only controller with a synthetic user header.

### 3. Execute the scenarios in a fourth terminal

```powershell
$ErrorActionPreference = 'Stop'
$ml = 'http://127.0.0.1:58000'
$logLayer = 'http://127.0.0.1:58082'
$headers = @{ 'X-User-Email' = 'smoke@example.invalid' }
$env:DB_PASSWORD = '<same disposable database password>'
$env:INTERNAL_API_KEY = '<same disposable ML key>'
$info = Invoke-RestMethod "$ml/api/v1/system/model"
if ($info.feature_count -ne 40 -or $info.active_threshold -ne -0.02) { throw 'Unexpected V2 contract' }

function Send-Scenario([string]$RawLog, [string]$Application) {
    $body = @{ rawLog = $RawLog; applicationName = $Application; source = 'SPRING_BOOT'; environment = 'DEVELOPMENT' } | ConvertTo-Json
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$logLayer/api/v1/logs" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    if ($response.StatusCode -ne 201) { throw 'Expected HTTP 201' }
    $response.Content | ConvertFrom-Json
}

# A. Healthy application log
$normal = Send-Scenario '2026-11-19T10:00:00.000+00:00 INFO 123 --- [main] com.example.App : User authenticated successfully for user_id=48291' 'user-service'
$accepted = Invoke-RestMethod "$logLayer/api/v1/logs/$($normal.id)" -Headers $headers
if ($accepted.anomaly -or $accepted.analysisStatus -ne 'COMPLETED' -or $accepted.llmAnalysisStatus -in @('PROCESSING', 'COMPLETED')) { throw 'Unexpected normal-log behavior' }

# B. Synthetic deadlock
$incident = Send-Scenario '2026-11-19T10:00:00.000+00:00 ERROR 123 --- [main] com.example.App : org.postgresql.util.PSQLException: ERROR: deadlock detected, SQLState 40001' 'payment-service'
if ($incident.modelVersion -ne '2.0.0-candidate' -or $incident.analysisStatus -ne 'COMPLETED') { throw 'V2 scoring did not complete' }
$incident | Select-Object id, level, anomaly, decisionScore, modelVersion
$deadline = (Get-Date).AddSeconds(180)
do {
    $stored = Invoke-RestMethod "$logLayer/api/v1/logs/$($incident.id)" -Headers $headers
    if ($stored.llmAnalysisStatus -in @('COMPLETED', 'FAILED')) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
if ($stored.llmAnalysisStatus -ne 'COMPLETED' -or -not $stored.summary -or -not $stored.suggestedFix) { throw "Diagnosis outcome: $($stored.llmAnalysisStatus)" }
if ($incident.id -notmatch '^\d+$') { throw 'Invalid returned log ID' }
$sql = "SELECT id, analysis_status, llm_analysis_status, llm_summary IS NOT NULL AS summary_saved, llm_root_cause IS NOT NULL AS cause_saved, llm_recommendation IS NOT NULL AS fix_saved, llm_severity IS NOT NULL AS severity_saved FROM logs WHERE id = $($incident.id);"
docker exec -e "PGPASSWORD=$env:DB_PASSWORD" devinsight-validation-db psql -U postgres -d log_layer_db -v ON_ERROR_STOP=1 -c $sql
```

Record the anomaly flag and score independently from the LLM status. The ERROR severity can schedule diagnosis even if V2 returns normal. Keep the SQL result flags and statuses without exporting private log contents.

For **C**, stop only the FastAPI terminal with Ctrl+C. Leave the two Java services running and send one INFO log in the fourth terminal:

```powershell
$unscored = Send-Scenario '2026-11-19T10:00:00.000+00:00 INFO 123 --- [main] com.example.App : Smoke test' 'user-service'
$storedFailure = Invoke-RestMethod "$logLayer/api/v1/logs/$($unscored.id)" -Headers $headers
if ($storedFailure.analysisStatus -ne 'FAILED') { throw 'Expected a persisted ML failure status' }
```

For **D**, restart FastAPI on port 58000 with `$env:ACTIVE_MODEL = 'v1'` in its terminal. Use the fourth terminal to check that its model-info and prediction endpoints actually serve V1:

```powershell
$legacy = Invoke-RestMethod "$ml/api/v1/system/model"
if ($legacy.feature_count -ne 41 -or $legacy.active_threshold -ne 0.0 -or $legacy.model_version -ne '1.0.0') { throw 'Unexpected V1 rollback contract' }
$payload = @{ timestamp = '2026-11-19T10:00:00Z'; level = 'INFO'; service_name = 'user-service'; message = 'User authenticated successfully' } | ConvertTo-Json
$prediction = Invoke-RestMethod "$ml/api/v1/predict" -Method Post -Headers @{ 'X-API-Key' = $env:INTERNAL_API_KEY } -ContentType 'application/json' -Body $payload
if ($prediction.model_version -ne '1.0.0' -or $prediction.prediction -notin @(-1, 1)) { throw 'V1 prediction failed' }
```

Restore V2 by restarting FastAPI with `ACTIVE_MODEL=v2` or stop the test services. When the test is finished and its evidence is saved, stop only the disposable database with `docker stop devinsight-validation-db`.

## Troubleshooting

| Symptom | What to check |
|---|---|
| FastAPI refuses startup | Verify the current directory, selected artifact paths, metadata checksum, and model version. |
| ML returns HTTP 401 | Match the Log Layer and FastAPI internal API keys. |
| ML returns HTTP 422 | Send timestamp, level, service name and message in the FastAPI schema. |
| PostgreSQL fails at startup | Check the mapped port, disposable password and database name. Verify pgvector in `llm_service_db`. |
| Ollama diagnosis fails or times out | Verify the service base URL, local model tags, available memory, and LLM Service logs. Check diagnosis status separately from ingestion. |
| Frontend cannot sign in | Verify the Gateway and User Service are running and share the expected JWT signing configuration. Confirm `VITE_API_BASE_URL`. |
