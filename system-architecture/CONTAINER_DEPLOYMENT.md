# Container deployment runbook

These configurations target an isolated single-node environment. PostgreSQL 16 and Ollama use persistent volumes. User Service and Log Layer validate their schemas at startup. Flyway execution and runtime role provisioning are separate operator actions. The Compose and Kubernetes defaults use synchronous ingestion, with external Slack and email disabled.

## Docker Compose

Use Docker with the Compose plugin. Create a private `.env` from [the example](../.env.example). Set fresh values for `DB_PASSWORD`, `LOG_LAYER_DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_API_KEY`, and `SLACK_WEBHOOK_ENCRYPTION_KEY`. The last must be 32 random bytes represented as 64 hex characters. Use separate credentials for this stack. The default `RAG_SYNCHRONIZE_ON_START=false` preserves the existing vector snapshot on startup.

The first-time sequence below uses a new Compose project and its own volumes. Verify the project name and volumes before executing any migration. For an existing installation, review migration history, restore a backup in isolation, and check schema compatibility first. First-run initialization scripts run only on empty PostgreSQL volumes.

```powershell
docker compose -p devinsight-lab config --quiet
docker compose -p devinsight-lab up -d --wait postgres ollama
docker compose -p devinsight-lab run --rm migrate-users migrate
docker compose -p devinsight-lab run --rm migrate-logs migrate
docker compose -p devinsight-lab run --rm log-db-role
docker compose -p devinsight-lab run --rm ollama-model-puller
$env:RAG_SYNCHRONIZE_ON_START = 'true'
docker compose -p devinsight-lab up -d --build --wait --wait-timeout 600
Remove-Item Env:RAG_SYNCHRONIZE_ON_START
```

Check each command's exit status before continuing. The migration services default to validation and are excluded from ordinary startup. The model puller prepares `phi3:mini` and `nomic-embed-text:latest`. Enable runbook synchronization only for a verified fresh vector store or a planned index update. After confirming the initial sync, recreate the LLM container with the ordinary configuration to clear its startup override. The Log Layer login provisioning grants the restricted tenant role without rotating an existing login's password. The [Flyway Docker reference](https://documentation.red-gate.com/flyway/reference/usage/flyway-docker) describes the migration image.

Check LLM startup logs and the expected chunk count in `llm_service_db.knowledge_chunks` after an initial runbook sync. HTTP health alone does not verify dense retrieval or completed inference.

Open `http://localhost:5173`. Nginx proxies same-origin `/api/` calls to the Gateway. Local collectors can also use `http://localhost:8080`. Set `DEVINSIGHT_FRONTEND_PORT` and `DEVINSIGHT_GATEWAY_PORT` before starting to choose unused loopback ports. The remaining service ports are internal to the Compose network.

```powershell
docker compose -p devinsight-lab ps
Invoke-RestMethod http://localhost:5173/healthz
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose -p devinsight-lab stop
```

### Upgrade an existing Compose stack for Loki V11

1. Verify the existing project, PostgreSQL container, and recorded Compose overrides. Use the same private environment and keep the existing PostgreSQL volume attached.
2. Back up `log_layer_db` in custom format and verify the copied archive before migrating. With the existing project's Compose arguments in `$compose`, run `docker compose @compose run --rm --no-deps -T migrate-logs migrate`. Confirm schema history reached 11.
3. Run `docker compose @compose build log-layer frontend`, then `docker compose @compose up -d --no-deps --no-build --wait log-layer frontend`. Check both services' readiness and the Gateway health endpoint.
4. Keep the current private Loki configuration or approve additional origins for the bound organization. In the UI, save the connection as the organization owner, then select an exact stream for each application. [Loki setup](../docs/LOKI_LOG_SOURCES.md) · [V11 upgrade and replay evidence](../docs/VERIFICATION.md#local-v11-loki-verification)

### Use Ollama already running on the Docker Desktop host

On Windows Docker Desktop, this option reuses an existing local Ollama server and its downloaded models. Verify `ollama list` contains `phi3:mini` and `nomic-embed-text:latest`. Keep the database in its own Compose project and volumes. Create a temporary override outside the checkout.

```yaml
services:
  llm-service:
    environment:
      OLLAMA_BASE_URL: http://host.docker.internal:11434
    depends_on: !override
      postgres:
        condition: service_healthy
```

Add `-f docker-compose.yml -f <temporary-override.yml>` before the command in each Compose invocation. Start only `postgres` for initial provisioning and skip the model-puller step. Then start the six application services by name so Compose does not also start its Ollama container.

```powershell
docker compose -p devinsight-lab -f docker-compose.yml -f $override up -d --build --wait --wait-timeout 600 ml-pipeline user-service llm-service log-layer api-gateway frontend
```

Use the ordinary first-sync setting for an empty vector store when you run that command. The host path is specific to Docker Desktop and requires the existing Ollama listener to be reachable from Docker.

## Registry images

Use the [development Compose path with host Ollama](#use-ollama-already-running-on-the-docker-desktop-host) to reproduce the published smoke test. The override passed locally with tagged images. Pulling matching application images from a remote registry remains unverified.

Earlier repository revisions shipped a standalone `docker-compose.prod.yml`. If your checkout has that version, use development Compose. The override used by these instructions contains `build: !reset null`. It must be used with the base `docker-compose.yml`. It swaps the six application builds for registry images and inherits the base health checks, migration jobs, restricted Log Layer role setup, and loopback bindings.

### Registry preflight

From the repository root, use the current [production environment template](../.env.prod.example) to create a private file outside the checkout and set `$registryEnv` to its absolute path. Use independent credentials, the registry account that owns the images, and an explicit `IMAGE_TAG` for the same source revision. Choose a separate project name and unused loopback ports if development Compose is already running. The registry override uses project-specific PostgreSQL and Ollama volumes with a `-registry` suffix.

```powershell
if (-not (Select-String -LiteralPath 'docker-compose.prod.yml' -SimpleMatch 'build: !reset null' -Quiet)) {
    throw 'This checkout has the older standalone Compose file. Use the development Compose path.'
}

$registryCompose = @('--env-file', $registryEnv, '-p', 'devinsight-registry', '-f', 'docker-compose.yml', '-f', 'docker-compose.prod.yml')
docker compose @registryCompose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Registry Compose configuration failed validation.' }
docker compose @registryCompose config --images
```

Check that the six application images use the intended registry account and tag. The image list shows the resolved names. Image availability requires a separate pull. [Docker Compose merge rules](https://docs.docker.com/reference/compose-file/merge/) · [Configuration command](https://docs.docker.com/reference/cli/docker/compose/config/)

### Start an isolated registry stack

After the preflight, use `docker compose @registryCompose` for each step in the [first-time provisioning sequence](#docker-compose). Check that the target volumes are new before running Flyway. Start PostgreSQL and bundled Ollama, apply the User Service and Log Layer migration sets, provision the restricted Log Layer login, and run the model-pull job. For the final application startup use `docker compose @registryCompose up -d --no-build --wait --wait-timeout 600` in place of the development build command. Use first-run runbook synchronization only for an empty vector store or a planned index update.

Check readiness and run a synthetic request before relying on the stack. Image availability, production ingress, TLS, and secret rotation still need validation.

## Kubernetes

The local Kubernetes manifests passed a single-node Docker Desktop rehearsal with pinned Ollama 0.17.1 and fresh model and PostgreSQL PVCs. The test used an isolated namespace and locally loaded images. [Cold-pull and replay evidence](../docs/VERIFICATION.md#kubernetes-cold-pull-and-http-replay)

The base renders a namespace, persistent volume claims, internal ClusterIP Services, application Deployments, and an Ollama model-pull Job. It references an externally provisioned `devinsight-secrets` Secret with the five credential keys listed for Compose. Provision it through the cluster's secret workflow or from a private environment file outside the checkout. The manifests contain references and omit secret values. PostgreSQL, User Service and LLM Service use the bundled development database administrator in this baseline. Log Layer uses the restricted runtime login.

1. Verify the active Kubernetes context and storage class. Use fresh PVCs in an isolated cluster for a first run. Apply [the namespace](../k8s/00-namespace.yaml) and create the Secret.
2. Apply [the ConfigMaps](../k8s/01-config-and-secrets.yaml), [PVCs](../k8s/02-storage/storage.yaml), [PostgreSQL](../k8s/03-infrastructure/postgres.yaml), and [Ollama](../k8s/03-infrastructure/ollama.yaml). Make the pinned `ollama/ollama:0.17.1` image available to the node. Wait for PostgreSQL readiness and confirm that the databases and vector schema exist. For a cold-pull check, confirm that `kubectl -n devinsight exec deployment/ollama -- ollama list` shows only the header on the fresh Ollama PVC. Then apply the [model-pull Job](../k8s/03-infrastructure/ollama-job.yaml).
3. Port-forward the isolated PostgreSQL Service on loopback `15432`. In another shell use Flyway with `FLYWAY_USER=postgres`, a private `FLYWAY_PASSWORD`, and `FLYWAY_CLEAN_DISABLED=true`. Start with `flyway info` against each intended database. For a verified fresh target, run the User Service V1 migrations against `jdbc:postgresql://127.0.0.1:15432/devinsight` and all Log Layer migrations included with the deployed image against `jdbc:postgresql://127.0.0.1:15432/log_layer_db`. The earlier Kubernetes rehearsal deployed V9; the current Log Layer source includes V11. Set `FLYWAY_LOCATIONS` to the matching module's `src/main/resources/db/migration` directory and validate each migration history.
4. Provision the Log Layer login using the shared [role script](../docker/bootstrap-log-role.sql). The PostgreSQL pod gets the runtime password through its Secret reference.

```powershell
Get-Content -Raw docker/bootstrap-log-role.sql | kubectl -n devinsight exec -i deployment/postgres -- psql -X -v ON_ERROR_STOP=1 -U postgres -d log_layer_db
kubectl -n devinsight wait --for=condition=complete job/ollama-model-puller --timeout=1800s
kubectl -n devinsight exec deployment/ollama -- ollama list
```

5. Make the `devinsight/<service>:local` application images available to your cluster using its local image-loading mechanism, or override each image in a private Kustomize overlay with an approved reference. The application image names match the Compose build tags. Kubernetes uses `RAG_VECTOR_STORE=postgres`. On a fresh vector store, set `RAG_SYNCHRONIZE_ON_START=true` in the overlay for the initial LLM rollout. After startup, query `llm_service_db.knowledge_chunks` as shown below. The tested runbook set produced 253 rows. After confirming the count, set synchronization back to `false` and restart the LLM deployment to preserve the indexed snapshot. Render and apply to the intended test cluster only after database provisioning and images are ready.

```powershell
kubectl kustomize k8s
kubectl apply -k k8s
kubectl -n devinsight get deployments,pods,services,jobs
kubectl -n devinsight port-forward service/frontend 5173:80
```

```powershell
kubectl -n devinsight exec deployment/postgres -- psql -X -U postgres -d llm_service_db -c "SELECT count(*) FROM public.knowledge_chunks;"
```

The model list must contain `nomic-embed-text:latest` and `phi3:mini`. The cold Job took 4 minutes 30 seconds in the isolated rehearsal. All six apps became ready. The LLM service completed the synthetic diagnosis on the first attempt in 129.3 seconds. HTTP v1 replay returned the original three log IDs and left counts at three logs, one incident, and one diagnosis job.

The LLM pod waits for both Ollama models before starting. HTTP startup and readiness probes cover each application. PostgreSQL and Ollama keep single-writer volume behavior during replacement. Applications do not need Kubernetes API tokens. The base does not provision Kafka. A Kafka-enabled overlay must supply a reachable authenticated broker, topics, ACLs, and runtime credentials.

## Validation scope

The [published Compose smoke](../docs/VERIFICATION.md#clean-copy-compose-smoke-and-http-replay) used a fresh PostgreSQL volume, locally built application images, host Ollama, and HTTP v1 replay. Separate [local container rehearsals](../docs/VERIFICATION.md#local-container-rehearsals) verified the registry override with locally tagged application images, bundled Ollama with preseeded models, and Kafka via an isolated overlay. The [Kubernetes cold-pull rehearsal](../docs/VERIFICATION.md#kubernetes-cold-pull-and-http-replay) verified the pinned Ollama runtime with a fresh model volume and PostgreSQL-backed RAG. Remote application image pulls and production or multi-node rollout remain unverified.
