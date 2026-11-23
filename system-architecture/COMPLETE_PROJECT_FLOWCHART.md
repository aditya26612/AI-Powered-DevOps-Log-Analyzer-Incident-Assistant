# DevInsight data flow

## Ingestion and investigation

```mermaid
flowchart LR
    C["Logback, Python or Winston collector"] --> G["API Gateway"]
    U["React operator workspace"] --> G
    G --> A["User Service and signed identity"]
    G --> L["Log Layer and tenant authorization"]
    L -->|"v1 direct processing"| P["Parse and analyze"]
    L -->|"v2 publish"| K["Optional Kafka broker"]
    K -->|"Consume, retry or isolate poison records"| P
    P --> M["FastAPI scoring"]
    P --> D["PostgreSQL logs, incidents and jobs"]
    D --> W["Fenced diagnosis worker"]
    W --> R["LLM Service and runbook retrieval"]
    R --> O["Ollama"]
    R --> W
    W -->|"Accept result and create eligible outbox intent"| D
    D --> N["Notification delivery worker"]
    N -->|"When enabled"| S["Slack webhook"]
    N -->|"Persist attempts"| D
    D --> L
```

## Acknowledgement and replay

```mermaid
sequenceDiagram
    participant C as Collector
    participant G as Gateway
    participant L as Log Layer
    participant K as Kafka
    participant P as PostgreSQL
    C->>G: Submit scoped events
    G->>L: Forward authorized request
    alt v1 synchronous batch
        L->>P: Commit source events and eligible incident state
        L-->>C: HTTP 200 with committed log IDs through Gateway
    else v2 asynchronous batch
        L->>K: Publish events
        par Confirm intake
            K-->>L: Confirm broker write to producer
            L-->>G: HTTP 202 with broker receipt
            G-->>C: Forward HTTP 202
        and Process available records
            K-->>L: Deliver records to consumer
            L->>P: Persist or deduplicate scoped events
            P-->>L: Confirm transaction commit
            L->>K: Commit next offset
        end
    end
```

The single-log API returns HTTP 201 after creation. Kafka consumers can begin processing independently of the HTTP response. A stable external event ID scopes replay to the original application and tenant.

## Separate states

| State | Owner | Meaning |
|---|---|---|
| Broker accepted | Kafka | V2 publish reached the broker |
| Scored or failed | Log Layer | Source log persists with its independent ML result |
| Open, acknowledged, or resolved | Incident record | Operator lifecycle and correlation state |
| Pending, running, retrying, succeeded, or terminal | Diagnosis queue | Persisted job and attempts |
| Delivered or delivery unknown | Notification outbox | Provider acknowledgement or unresolved dispatch uncertainty |

Diagnosis produces guidance for an operator. A notification retry after unknown delivery requires explicit confirmation because the provider may already have accepted the original request.
