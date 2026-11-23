# External and inter-service contracts

| Caller → destination | Contract | Boundary |
|---|---|---|
| Operator → Gateway → User Service | `/api/v1/auth/**` and `/api/v1/users/**` | Public registration assigns the server default role. Signed JWTs carry operator identity. |
| Operator → Gateway → Log Layer | `/api/v1/organizations/**` plus log, analyzer, and integration routes | Organization membership and role checks apply in Log Layer. |
| Collector → Gateway → Log Layer | `X-API-Key` with `POST /api/v1/logs` or one of the batch APIs | Authoritative credential binding checks application identity, environment, and credential status. |
| Log Layer → FastAPI | `POST /api/v1/predict` and internal `X-API-Key` | Model score and version travel back to ingestion. Unavailable inference remains a scoring failure. |
| Log Layer → LLM Service | Internal analysis request with service authentication | A worker sends bounded incident context. Ollama output is validated before acceptance. |
| LLM Service → PostgreSQL/Ollama | Runbook retrieval and embeddings against `llm_service_db` and the configured Ollama URL | The vector store holds shared runbook knowledge. Tenant log context is scoped before inference. |
| Log Layer → Kafka | Raw-topic publishing and consumer-group reads | Broker confirmation, replay, offset handling, and dead-letter handling are separate from HTTP persistence. |
| Notification worker → configured Slack webhook | Bounded HTTP request with application-scoped encrypted destination | Acknowledgement, retries, cooldown, and uncertain delivery persist independently. |

## Identity and routing

The Gateway removes client-provided internal identity headers. Signed operator tokens preserve the email subject and can carry the numeric `user_id` issued by User Service. Tenant routes require the signed numeric identity and verify organization membership. Ingestion credentials grant submission access inside the application binding and do not grant access to operator APIs. Internal keys and downstream authentication carry service identity across the approved routes.

## Failures and recovery

- Synchronous v1 returns committed log IDs. V2 returns the broker receipt. Retry the same logical event with its original event ID.
- Kafka unavailability can reject v2 submission while synchronous processing remains available.
- Database failure prevents authoritative processing from completing. Acknowledged Kafka data still needs consumption within topic retention.
- Inference failure can leave a persisted log with failed scoring. Diagnosis has separate bounded retries, leases and attempts.
- A successful Slack HTTP response is provider acceptance. If delivery is uncertain, inspect its attempt history before an explicitly confirmed retry.

See the [system flow](COMPLETE_PROJECT_FLOWCHART.md), [current endpoints](../README.md#api-reference) and [verified local recovery scenarios](../docs/VERIFICATION.md).
