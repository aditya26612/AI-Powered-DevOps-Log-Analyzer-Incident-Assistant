# Log Layer internals

Log Layer owns tenant membership and application configuration, source telemetry and scoring metadata, incident correlation, diagnosis state, notification attempts, and observability reads. It runs on Spring Boot and PostgreSQL.

## Ingestion and tenant isolation

| Component | Responsibility |
|---|---|
| `TenantIdentity`, `TenantSqlScope`, `TenantRuntimeGuard` | Verify request scope, constrain tenant SQL transactions, and reject unsafe runtime roles. |
| `ExternalBatchIngestion` and `KafkaIngress` | Validate bounded application-scoped batches. V1 returns after commit. V2 submits to the broker. |
| `LokiSourceController`, `LokiConnections`, `LokiSources`, `LokiPoller`, `LokiClient` | Validate owner-only connection edits, configure application selectors, query Loki and commit imports with their checkpoints. |
| Parser and inference services | Normalize source data and obtain scores from the pinned ML service. Statistical results and operational severity remain separate. |
| `IncidentPolicy` and `IncidentCorrelation` | Apply organization and environment boundaries, reported trace context, and application signatures. |
| `ObservabilityQueries` | Provide scoped summaries, filters and source detail. Credential-like source text is masked when returned from preview endpoints. |

PostgreSQL enforces row-level security under a restricted runtime login. External event identity makes replay safe for the original tenant and application. Preserve individually committed source events even when several share an incident.

## Loki pull and replay

`LokiConnections` restricts the organization owner's selection to operator-approved origins. `LokiClient` queries forward in nanosecond time ranges, rejects redirects and keeps operator-provided credentials pinned to the default origin. `LokiPoller` narrows a full response window and retries a failure without advancing it. `LokiSources` uses the shared external writer and commits the checkpoint and counters in the same transaction as the import. An endpoint change starts a fresh five-minute window, retains persisted logs, and increments source revisions so results fetched for the previous endpoint cannot commit after the switch. [Loki source guide](../../docs/LOKI_LOG_SOURCES.md)

## Incidents, diagnosis and delivery

Correlation uses event time. The default rolling gap is five minutes, with a thirty-minute span cap and configured bounds on old and future-skewed events. Reported traces help group evidence and do not authenticate a request. An incident can retain one durable diagnosis job across a replay.

`DiagnosisQueue` and `DiagnosisWorker` claim work with a lease and fence completion to the current attempt. `DiagnosisContext` assembles scoped evidence for the LLM Service. The accepted result and eligible notification intent commit together. `NotificationQueue` and `NotificationDeliveryWorker` record attempts, bounded retry and shared destination cooldowns. Unknown delivery requires an explicit confirmed retry.

## Schema and operator reads

Flyway migrations through V9 establish tenant RLS, incident membership, diagnosis attempts, transactional outbox delivery, and destination cooldowns. V10 adds RLS-protected application Loki sources and sync state. V11 adds the RLS-protected organization endpoint. The runtime login inherits the restricted tenant role and starts only after independent schema provisioning. `/api/v1/organizations/{organization}/observability` and the incident endpoints apply organization scope and bounded reads. The frontend displays scoring, diagnosis and delivery as separate states.
