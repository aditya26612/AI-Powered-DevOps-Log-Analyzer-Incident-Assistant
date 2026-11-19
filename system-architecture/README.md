# DevInsight System Architecture — Master Documentation Index

> Comprehensive technical documentation of DevInsight's 5 microservices, internal package structures, design patterns, inter-service communication protocols, and architectural flowcharts.

---

## Documentation Directory Map

```text
system-architecture/
├── README.md                           # This navigation index
├── COMPLETE_PROJECT_FLOWCHART.md       # Master end-to-end Mermaid flowcharts & state machines
├── EXTERNAL_INTERACTIONS.md            # Inter-service protocols, network boundaries, & failure modes
├── api-gateway/
│   └── INTERNAL_ARCHITECTURE.md        # Netty, WebFlux, JwtAuthenticationWebFilter, routing
├── user-service/
│   └── INTERNAL_ARCHITECTURE.md        # Spring Security, BCrypt, IUserRepo, JWT issuance
├── log-layer/
│   └── INTERNAL_ARCHITECTURE.md        # Polyglot parsers, WebClient adapters, async Slack routing
├── ml-pipeline/
│   └── INTERNAL_ARCHITECTURE.md        # FastAPI, FeatureOrchestrator, Shannon Entropy, Isolation Forest
└── llm-service/
    └── INTERNAL_ARCHITECTURE.md        # Hybrid RAG (Dense + BM25 + RRF), Ollama, StringTemplate
```

---

## Service Overview & Quick Reference

| Module / Service | Tech Stack | Port | Internal Architecture Guide | Core Design Patterns |
|---|---|---|---|---|
| **API Gateway** | Spring Boot 3, WebFlux, Netty | `8080` | [api-gateway/INTERNAL_ARCHITECTURE.md](api-gateway/INTERNAL_ARCHITECTURE.md) | Gateway Routing, Chain of Responsibility, Exchange Mutation |
| **User Service** | Spring Boot 3, Spring Security | `8081` | [user-service/INTERNAL_ARCHITECTURE.md](user-service/INTERNAL_ARCHITECTURE.md) | Repository, DTO, Password Hashing Strategy, Global Exception Handling |
| **Log Layer** | Spring Boot 3, JPA, WebClient | `8082` | [log-layer/INTERNAL_ARCHITECTURE.md](log-layer/INTERNAL_ARCHITECTURE.md) | Strategy (Parsers), Factory Method, Template Method, Reactive Adapter, Specification |
| **ML Pipeline** | Python 3.11, FastAPI, scikit-learn | `8000` | [ml-pipeline/INTERNAL_ARCHITECTURE.md](ml-pipeline/INTERNAL_ARCHITECTURE.md) | Singleton Model Cache, Extraction Strategy, Transformation Pipeline, Dependency Injection |
| **LLM Service** | Spring Boot 3, Spring AI, Ollama | `8084` | [llm-service/INTERNAL_ARCHITECTURE.md](llm-service/INTERNAL_ARCHITECTURE.md) | Hybrid Retrieval Fusion (RRF), Provider Strategy, Template Builder, Defensive Parsing |

---

## Architectural Deep Dives

* **[EXTERNAL_INTERACTIONS.md](EXTERNAL_INTERACTIONS.md):**
  Details how the microservices communicate over the network, including the trusted header enrichment contract (`X-User-Email`), inter-service payload schemas, synchronous vs asynchronous boundary partitioning (ingestion acceptance and background diagnosis), and fault-tolerance fallback paths.

* **[COMPLETE_PROJECT_FLOWCHART.md](COMPLETE_PROJECT_FLOWCHART.md):**
  Contains complete visual diagrams:
  1. Master End-to-End System Flowchart (Client $\rightarrow$ Gateway $\rightarrow$ Log Layer $\rightarrow$ ML $\rightarrow$ DB $\rightarrow$ RAG $\rightarrow$ Ollama $\rightarrow$ Slack $\rightarrow$ UI).
  2. Request-Response Lifecycle Sequence Diagram with timing boundaries.
  3. Log Entity State Transition Machine (`RECEIVED` $\rightarrow$ `PERSISTED` $\rightarrow$ `ANOMALY_DETECTED` $\rightarrow$ `LLM_COMPLETED` $\rightarrow$ `SLACK_ROUTED`).
  4. Core 3 Pillars of DevInsight.
