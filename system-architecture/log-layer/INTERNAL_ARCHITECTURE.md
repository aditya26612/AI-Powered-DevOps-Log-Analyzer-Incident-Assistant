# Log Layer internals

Log Layer owns tenant membership and application configuration, source telemetry and scoring metadata, incident correlation, diagnosis state, notification attempts, and observability reads. It runs on Spring Boot and PostgreSQL.

## Ingestion and tenant isolation

| Component | Responsibility |
|---|---|
| `TenantIdentity`, `TenantSqlScope`, `TenantRuntimeGuard` | Verify request scope, constrain tenant SQL transactions, and reject unsafe runtime roles. |
| `ExternalBatchIngestion` and `KafkaIngress` | Validate bounded application-scoped batches. V1 returns after commit. V2 submits to the broker. |
| Parser and inference services | Normalize source data and obtain scores from the pinned ML service. Statistical results and operational severity remain separate. |
| `IncidentPolicy` and `IncidentCorrelation` | Apply organization and environment boundaries, reported trace context, and application signatures. |
| `ObservabilityQueries` | Provide scoped summaries, filters and source detail. Credential-like source text is masked when returned from preview endpoints. |

PostgreSQL enforces row-level security under a restricted runtime login. External event identity makes replay safe for the original tenant and application. Preserve individually committed source events even when several share an incident.

## Incidents, diagnosis and delivery

Correlation uses event time. The default rolling gap is five minutes, with a thirty-minute span cap and configured bounds on old and future-skewed events. Reported traces help group evidence and do not authenticate a request. An incident can retain one durable diagnosis job across a replay.

`DiagnosisQueue` and `DiagnosisWorker` claim work with a lease and fence completion to the current attempt. `DiagnosisContext` assembles scoped evidence for the LLM Service. The accepted result and eligible notification intent commit together. `NotificationQueue` and `NotificationDeliveryWorker` record attempts, bounded retry and shared destination cooldowns. Unknown delivery requires an explicit confirmed retry.

## Schema and operator reads

Flyway migrations through V9 establish tenant RLS, incident membership, diagnosis attempts, transactional outbox delivery, and destination cooldowns. The runtime login inherits the restricted tenant role and starts only after independent schema provisioning. `/api/v1/organizations/{organization}/observability` and the incident endpoints apply organization scope and bounded reads. The frontend displays scoring, diagnosis and delivery as separate states.
