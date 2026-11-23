# DevInsight system architecture

DevInsight turns application telemetry into persisted source logs, anomaly scores, tenant-scoped incidents, and advisory diagnoses. PostgreSQL stores authoritative records. Kafka is an optional intake transport. The React workspace reads scoped APIs through the Gateway.

| Component | Responsibility | Local port |
|---|---|---:|
| [API Gateway](api-gateway/INTERNAL_ARCHITECTURE.md) | Verifies operator JWTs and application credentials and routes requests | 8080 |
| [User Service](user-service/INTERNAL_ARCHITECTURE.md) | Registers users, authenticates credentials and issues signed tokens | 8081 |
| [Log Layer](log-layer/INTERNAL_ARCHITECTURE.md) | Owns tenant authorization, telemetry, incidents, diagnosis jobs, notifications, and dashboard reads | 8082 |
| [ML Pipeline](ml-pipeline/INTERNAL_ARCHITECTURE.md) | Loads and serves the pinned anomaly model | 8000 |
| [LLM Service](llm-service/INTERNAL_ARCHITECTURE.md) | Retrieves runbooks and produces structured diagnoses with local Ollama | 8084 |
| React frontend | Overview, Log Explorer, incident workspace, and application integrations | 5173 |

Start with the [system and sequence diagrams](COMPLETE_PROJECT_FLOWCHART.md), then use the [network and API contracts](EXTERNAL_INTERACTIONS.md) for integration details. The [container runbook](CONTAINER_DEPLOYMENT.md) explains Compose and Kubernetes configuration, database preparation, readiness, and current deployment limits. [Local verification evidence](../docs/VERIFICATION.md) documents the tested Windows fixture and its performance envelope.
