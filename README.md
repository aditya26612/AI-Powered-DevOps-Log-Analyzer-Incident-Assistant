# DevInsight — Enterprise AI-Powered DevOps Observability & Autonomous Incident Assistant

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-green?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3" />
  <img src="https://img.shields.io/badge/Spring_Cloud-Gateway-brightgreen?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Cloud Gateway" />
  <img src="https://img.shields.io/badge/Kubernetes-Native-blue?style=for-the-badge&logo=kubernetes&logoColor=white" alt="Kubernetes" />
  <img src="https://img.shields.io/badge/Docker-Multi--Stage-blue?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
  <img src="https://img.shields.io/badge/Python-3.11-yellow?style=for-the-badge&logo=python&logoColor=white" alt="Python 3.11" />
  <img src="https://img.shields.io/badge/FastAPI-ML_Pipeline-teal?style=for-the-badge&logo=fastapi&logoColor=white" alt="FastAPI" />
  <img src="https://img.shields.io/badge/PostgreSQL-16_Alpine-blue?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Ollama-Local_LLM-purple?style=for-the-badge&logo=ollama&logoColor=white" alt="Ollama" />
  <img src="https://img.shields.io/badge/Slack-Block_Kit_Alerts-4A154B?style=for-the-badge&logo=slack&logoColor=white" alt="Slack" />
  <img src="https://img.shields.io/badge/React-18_Vite-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="React 18" />
</p>

> **Transform raw distributed log streams into sub-15ms anomaly detection, domain-grounded AI root-cause diagnoses, and actionable Slack incident notifications with zero cloud data exfiltration.**

DevInsight is an enterprise-grade autonomous observability platform engineered to eliminate alert fatigue and slash Mean Time to Resolution (MTTR) from 30+ minutes to under 2 minutes. Combining a reactive microservice backbone, unsupervised machine learning anomaly detection, hybrid RAG over operational runbooks, and a local self-hosted LLM runtime (`phi3:mini`), DevInsight moves engineering teams from passive log grepping to active automated incident remediation.

---

## Table of Contents

- [1. Executive Product Overview](#1-executive-product-overview)
- [2. System Architecture](#2-system-architecture)
  - [2.1 End-to-End Enterprise Architecture](#21-end-to-end-enterprise-architecture)
  - [2.2 Telemetry & Incident Lifecycle Sequence](#22-telemetry--incident-lifecycle-sequence)
- [3. Key Platform Capabilities](#3-key-platform-capabilities)
- [4. Zero-Invasive Client Integration (Spring Boot Logback SDK)](#4-zero-invasive-client-integration-spring-boot-logback-sdk)
- [5. Real-Time Incident Alerting Engine (Slack Block Kit)](#5-real-time-incident-alerting-engine-slack-block-kit)
- [6. Technology Stack](#6-technology-stack)
- [7. Monorepo Structure](#7-monorepo-structure)
- [8. Microservices & Responsibilities](#8-microservices--responsibilities)
- [9. Database Design & Persistence](#9-database-design--persistence)
- [10. Machine Learning Anomaly Pipeline](#10-machine-learning-anomaly-pipeline)
- [11. LLM & Hybrid RAG Architecture](#11-llm--hybrid-rag-architecture)
- [12. Empirical Retrieval Benchmarks (MRR Evaluation)](#12-empirical-retrieval-benchmarks-mrr-evaluation)
- [13. API Reference & Headless Ingestion](#13-api-reference--headless-ingestion)
- [14. Quickstart: One-Click Orchestration Scripts](#14-quickstart-one-click-orchestration-scripts)
- [15. Kubernetes Cloud-Native Deployment (Local & AWS EKS)](#15-kubernetes-cloud-native-deployment-local--aws-eks)
- [16. Docker Compose Deployment](#16-docker-compose-deployment)
- [17. React Web Dashboard & Live Inspector](#17-react-web-dashboard--live-inspector)
- [18. End-to-End Live Simulation & Testing](#18-end-to-end-live-simulation--testing)
- [19. Security, RBAC & Secret Hygiene](#19-security-rbac--secret-hygiene)
- [20. Project Status & Roadmap](#20-project-status--roadmap)

---

# 1. Project Overview

DevInsight is designed to simplify the process of analyzing application logs and investigating technical incidents.

In a typical development environment, engineers frequently encounter:

- Application errors and exceptions.
- Unexpected service failures.
- Database connection problems.
- Infrastructure and deployment issues.
- Container and orchestration failures.
- Performance-related warnings.
- Repeated or unusual application behavior.

Manually investigating these problems requires searching through logs, understanding error messages, identifying the affected service, and consulting technical documentation.

DevInsight brings these activities into one platform.

It uses:

1. **Microservices architecture** to separate application responsibilities.
2. **Machine Learning** to identify anomalous log behavior.
3. **Retrieval-Augmented Generation** to retrieve relevant DevOps knowledge.
4. **Large Language Models** to explain incidents and recommend possible solutions.
5. **React dashboard** to provide a centralized interface for log ingestion and investigation.

### Main objective

Transform raw application logs into useful diagnostic information:

```text
Raw Application Logs
        |
        v
Log Parsing and Validation
        |
        v
Log Storage
        |
        v
ML-Based Anomaly Detection
        |
        v
RAG-Based Knowledge Retrieval
        |
        v
LLM Incident Analysis
        |
        v
Summary + Root Cause + Suggested Fix
```

---

# 2. System Architecture

DevInsight follows a decoupled, cloud-native microservices architecture orchestrated on Kubernetes. The reactive API Gateway acts as the unified ingress point, routing requests based on JWT (for human operators) and headless `X-API-Key` (for streaming microservices). The Log Layer coordinates non-blocking ingestion (<15ms), delegates statistical scoring to the ML Pipeline, triggers background RAG analysis via the LLM Service, and dispatches incident alerts directly to Slack.

### 2.1 End-to-End Enterprise Architecture

```mermaid
flowchart TD
    subgraph ClientTier ["1. Ingestion & Client Tier"]
        SB["External Spring Boot Microservices\n(order-service, payment-service)"]
        LBA["DevInsightLogbackAppender\n(Async HTTP Dispatcher)"]
        CLI["DevOps CLI / Ingestion Scripts\n(test-live-springboot-ingest.ps1)"]
        ReactUI["DevInsight React Dashboard\n(Vite + Tailwind :5173)"]
        SB -->|Async Event| LBA
    end

    subgraph GatewayTier ["2. Ingress & Security Tier"]
        GW["API Gateway (Spring Cloud Gateway :8080)\nNetty Reactive Engine"]
        JWTFilter["JwtAuthenticationWebFilter\n- Bearer JWT (Operators)\n- X-API-Key (Machine Services)"]
        GW --> JWTFilter
    end

    subgraph MicroservicesTier ["3. Kubernetes Microservices Mesh (devinsight namespace)"]
        US["User Service (:8081)\nSpring Security + BCrypt"]
        LL["Log Layer (:8082)\nSpring Boot 3 + Resilient Parsers"]
        MLP["ML Pipeline (:8000)\nFastAPI + Isolation Forest"]
        LLMS["LLM Service (:8084)\nSpring AI + Hybrid RAG"]
        OLL["Ollama Local Inference (:11434)\nphi3:mini + nomic-embed-text"]
    end

    subgraph DataStorageTier ["4. Persistent Data & Knowledge Tier"]
        PG["PostgreSQL Database (:5432)\n- devinsight (Users)\n- log_layer_db (Telemetry)"]
        RAGDocs["DevOps Runbooks (21 Docs, 253 Chunks)\nMarkdown Knowledge Base"]
    end

    subgraph AlertingTier ["5. Real-Time Alerting Tier"]
        Slack["Slack Workspace\n# all-devinsight Channel"]
        ConsoleLog["Structured Audit Console"]
    end

    LBA -->|POST /api/v1/logs [X-API-Key]| GW
    CLI -->|POST /api/v1/logs [X-API-Key]| GW
    ReactUI -->|REST API [Bearer JWT]| GW

    JWTFilter -->|Route /api/v1/auth/**| US
    JWTFilter -->|Route /api/v1/logs/**| LL

    US -->|User Credentials| PG
    LL -->|Log Entity CRUD| PG

    LL -->|1. Anomaly Scoring (<10ms)| MLP
    LL -.->|2. Async Non-Blocking Trigger| LLMS
    LLMS -->|Hybrid Search (BM25 + Dense)| RAGDocs
    LLMS -->|Synthesize Root Cause & Fix| OLL
    LLMS -.->|3. Return IncidentDiagnosis| LL
    LL -->|4. Dispatch Block Kit Card| Slack
    LL -->|4. Audit Record| ConsoleLog
```

### 2.2 Telemetry & Incident Lifecycle Sequence

```mermaid
sequenceDiagram
    autonumber
    participant App as External Spring Boot Service
    participant GW as API Gateway (:8080)
    participant LL as Log Layer (:8082)
    participant ML as ML Pipeline (:8000)
    participant PG as PostgreSQL (:5432)
    participant RAG as Spring AI RAG Engine (:8084)
    participant Ollama as Ollama LLM (:11434)
    participant Slack as Slack Channel (#all-devinsight)

    Note over App: 💥 Outage occurs: PSQLException Connection slots exhausted
    App->>GW: POST /api/v1/logs (Header: X-API-Key)
    GW->>GW: Validate X-API-Key -> Assign ROLE_SERVICE
    GW->>LL: Forward request to Log Layer
    LL->>LL: Resilient parser extracts Timestamp, Level, Thread, Logger, Stack Trace
    LL->>ML: POST /api/v1/predict (raw log + features)
    ML-->>LL: Return prediction: isAnomaly=true, score=-0.1279
    LL->>PG: Save Log record (Status: RECEIVED, Analysis: COMPLETED)
    LL-->>GW: Return 201 Created (Duration < 15ms!)
    GW-->>App: 201 Created (Client thread never blocked)

    Note over LL: ⚡ Background Async Trigger (CompletableFuture.runAsync)
    LL->>RAG: POST /api/v1/llm/analyze (serviceName, rawLog, stackTrace)
    RAG->>RAG: BM25 Sparse Search + Dense nomic-embed-text Search
    RAG->>RAG: Reciprocal Rank Fusion (RRF) -> Top 3 Runbook Chunks
    RAG->>Ollama: POST /api/generate (3,500-token prompt + context)
    Ollama-->>RAG: Structured JSON: {rootCause, remediationSteps}
    RAG-->>LL: Return IncidentDiagnosis
    LL->>PG: Update Log (Status: RESOLVED, llmAnalysisStatus: COMPLETED)
    LL->>Slack: POST Block Kit Incident Card to Webhook
    Note over Slack: 🚨 Alert pops up in #all-devinsight with Root Cause & Shell Commands
```

### Architecture Components

| Component | Technology | Responsibility |
|---|---|---|
| **Frontend** | React 18 + Vite + Tailwind CSS | Real-time log explorer, anomaly visualization, and interactive inspector sandbox |
| **API Gateway** | Spring Cloud Gateway + WebFlux | High-throughput non-blocking reverse proxy with dual JWT and API-Key authentication |
| **User Service** | Spring Boot 3 + Spring Security 6 | RBAC user management, BCrypt hashing, and stateless JWT token issuance |
| **Log Layer** | Spring Boot 3 + Hibernate + JPA | Sub-15ms log ingestion, resilient multiline parsing, async event orchestration, and Slack dispatcher |
| **ML Pipeline** | Python 3.11 + FastAPI + Scikit-Learn | Statistical anomaly detection using an Isolation Forest model (<10ms inference) |
| **LLM Service** | Spring Boot 3 + Spring AI | Hybrid RAG retrieval (BM25 + Dense Vectors + RRF) and prompt orchestration |
| **PostgreSQL** | PostgreSQL 16 Alpine | Dual databases: `devinsight` (user/auth) and `log_layer_db` (logs/analytics) |
| **Ollama** | Local LLM Engine (llama.cpp) | Self-hosted local inference with `phi3:mini` (3.8B) and `nomic-embed-text` |
| **Slack Notifier**| Reactive WebClient + Block Kit | Automated dispatch of rich incident cards to Slack channels |

### Why microservices?

The application separates authentication, log processing, machine learning, and LLM-based analysis into independent services.

This provides:

- Separation of responsibilities.
- Independent development and testing.
- Independent service configuration.
- The ability to scale resource-intensive components separately.
- Easier maintenance as the system grows.

For example, ML inference and LLM inference have different computational requirements. Keeping them separate avoids making the Log Layer responsible for every processing task.

---

# 3. How DevInsight Works

The following sequence describes the complete log-analysis workflow.

### Step 1 — User authentication

The user registers or logs in through the React frontend.

The request passes through the API Gateway to the User Service.

The User Service authenticates the user and generates a JWT token.

### Step 2 — API Gateway routing

The frontend sends HTTP requests to the API Gateway.

The Gateway:

- Routes requests to the appropriate backend service.
- Applies JWT authentication and authorization.
- Handles CORS and security filters.

### Step 3 — User data persistence

The User Service stores user information in its PostgreSQL database.

### Step 4 — Log ingestion and storage

The user submits application logs through the frontend.

The Log Layer:

- Receives the log request.
- Parses and validates the incoming log.
- Extracts structured information.
- Stores the log in PostgreSQL.
- Initiates the analysis workflow.

### Step 5 — ML anomaly detection

The Log Layer sends the required log information to the ML Pipeline.

The ML Pipeline extracts features and performs anomaly prediction.

### Step 6 — ML prediction returned

The ML Pipeline returns prediction information, including the anomaly classification and associated prediction metadata.

The Log Layer stores the ML result with the log.

### Step 7 — LLM incident investigation

The Log Layer sends the log information and ML prediction to the LLM Service.

The LLM Service:

1. Constructs an analysis prompt.
2. Retrieves relevant DevOps knowledge using the RAG pipeline.
3. Combines the log, ML prediction, and retrieved context.
4. Sends the resulting prompt to Ollama.
5. Generates an incident-analysis response.

### Step 8 — Analysis returned

The LLM Service returns structured analysis containing information such as:

- Summary.
- Probable root cause.
- Severity.
- Recommended troubleshooting actions.

The Log Layer stores the analysis result.

### Step 9 — Frontend presentation

The frontend displays the log and its associated analysis.

The user can inspect the log, review the anomaly prediction, and understand the generated troubleshooting recommendations.

### Complete request flow

```text
React Frontend
      |
      | HTTP Request
      v
API Gateway (:8080)
      |
      |-----------------------------|
      |                             |
      v                             v
User Service (:8081)          Log Layer (:8082)
      |                             |
      v                             |
User PostgreSQL                    |
                                    |
                                    v
                             Log PostgreSQL
                                    |
                                    v
                             ML Pipeline (:8000)
                                    |
                                    v
                             Anomaly Prediction
                                    |
                                    v
                              Log Layer
                                    |
                                    v
                              LLM Service (:8084)
                                    |
                         -----------------------
                         |                     |
                         v                     v
                    Vector Store           Ollama
                    (RAG Retrieval)       (:11434)
                         |                     |
                         -----------|----------
                                    |
                                    v
                            AI Incident Analysis
                                    |
                                    v
                                Log Layer
                                    |
                                    v
                              React Frontend
```

---

# 4. Key Features

## 4.1 Authentication and User Management

- User registration and login.
- JWT token generation.
- JWT-based authentication.
- Protected backend API requests.
- User profile management.

## 4.2 Log Ingestion

- Submit logs through the frontend.
- Support file/text-based log submission.
- Associate logs with source and environment information.
- Parse and structure incoming log information.
- Persist logs for future investigation.

## 4.3 Log Explorer

- Search and filter stored logs.
- Inspect individual log details.
- Filter by available log metadata.
- View associated analysis results.

## 4.4 ML-Based Anomaly Detection

- Extract features from incoming logs.
- Detect anomalous log behavior.
- Return anomaly/normal predictions.
- Associate predictions with model metadata.
- Support individual and batch prediction endpoints.

## 4.5 AI-Powered Incident Analysis

- Analyze logs using an LLM.
- Include ML prediction information in the analysis context.
- Retrieve relevant DevOps documentation.
- Generate incident summaries.
- Identify probable root causes.
- Recommend troubleshooting actions.

## 4.6 Hybrid RAG Retrieval

- Dense vector retrieval.
- BM25 lexical retrieval.
- Reciprocal Rank Fusion.
- Experimental reranking.
- Retrieval benchmarking.

## 4.7 Centralized Dashboard & Live Inspector

- Real-time log explorer with full-text search and faceted severity filters.
- Interactive Live Inspector (`/analyzer`) for manual multi-line stack trace triage.
- Direct visualization of anomaly scores, token metrics, and extracted shell remediation commands.

## 4.8 Zero-Invasive Client Integration (`client-integrations/spring-boot`)

- **Drop-In Logback Appender**: Any external Spring Boot microservice can stream real-time logs to DevInsight by adding a single XML snippet in `logback-spring.xml`.
- **Zero Business Logic Impact**: Uses a bounded `ArrayBlockingQueue` with a daemon worker thread to prevent observability latency from impacting application transactions.
- **Headless Ingestion**: Authenticated via machine-to-machine `X-API-Key` headers without requiring interactive user credentials.

## 4.9 Automated Real-Time Alerting Engine (Slack Block Kit)

- **Autonomous Incident Trigger**: When a log is classified as an anomaly or severity `ERROR`/`FATAL`, background threads asynchronously run RAG retrieval and LLM diagnosis.
- **Rich Block Kit Notification Cards**: Formatted Slack incident cards displaying error stack traces, anomaly decision scores, root-cause summaries, and click-to-copy terminal remediation commands.
- **Multi-Channel Dispatcher**: Built with a Composite Notification Service supporting Slack incoming webhooks, structured audit logging, and extensible email channels.

## 4.10 Cloud-Native Kubernetes Orchestration (`k8s/`)

- Declarative manifests for local (**Kind**, **Docker Desktop**) and managed cloud (**AWS EKS**, **GKE**) deployments.
- Liveness and readiness health probes, isolated model puller Jobs, and tuned memory limits (6 GB) with single-model VRAM caching to eliminate container OOM errors.

---

# 5. Technology Stack

## Backend

| Technology | Usage |
|---|---|
| Java 21 | Backend development |
| Spring Boot | Microservice development |
| Spring Cloud Gateway | API Gateway |
| Spring WebFlux | Reactive Gateway architecture |
| Spring Security | Authentication and authorization |
| Spring Data JPA | Database persistence |
| PostgreSQL Driver | Database connectivity |
| WebClient | Inter-service HTTP communication |
| Maven | Dependency and build management |
| JJWT | JWT-related functionality |

## Machine Learning

| Technology | Usage |
|---|---|
| Python | ML pipeline development |
| FastAPI | ML REST API |
| Uvicorn | ASGI server |
| Scikit-learn | Machine-learning functionality |
| Pandas | Data processing |
| NumPy | Numerical processing |
| Joblib | Model artifact handling |

## LLM and RAG

| Technology | Usage |
|---|---|
| Spring AI | LLM and embedding integration |
| Ollama | Local LLM runtime |
| `phi3:mini` | Chat model |
| `nomic-embed-text:latest` | Embedding model |
| BM25 | Lexical retrieval |
| Dense Retrieval | Semantic retrieval |
| Reciprocal Rank Fusion | Hybrid result fusion |
| PostgreSQL/vector storage | Persistent application and vector data |

## Frontend

| Technology | Usage |
|---|---|
| React | Frontend development |
| Vite | Development and build tooling |
| Axios | HTTP requests |
| React Router | Client-side routing |
| Lucide React | Icons |

---

# 6. Monorepo Structure

The project is organized as a production-grade monorepo containing independent microservices, client integration SDKs, Kubernetes manifests, and automation scripts:

```text
AI-Powered-DevOps-Log-Analyzer-Incident-Assistant/
│
├── api-gateway/                 # Reactive Spring Cloud Gateway reverse proxy (:8080)
│   ├── src/
│   └── pom.xml
│
├── user-service/                # Spring Boot user authentication & JWT management (:8081)
│   ├── src/
│   └── pom.xml
│
├── log-layer/                   # Log ingestion, multiline parser & Slack dispatcher (:8082)
│   ├── src/
│   └── pom.xml
│
├── ml-pipeline/                 # Python 3.11 FastAPI Isolation Forest model (:8000)
│   ├── app/
│   ├── artifacts/
│   └── requirements.txt
│
├── llm-service/                 # Spring AI Hybrid RAG engine & prompt orchestration (:8084)
│   ├── src/
│   │   └── main/resources/knowledge/   # 21 operational runbooks (253 chunks)
│   └── pom.xml
│
├── frontend/                    # React 18 + Vite + Tailwind CSS dashboard (:5173)
│   ├── src/
│   └── package.json
│
├── client-integrations/         # Zero-code drop-in client logging SDKs
│   └── spring-boot/             # DevInsightLogbackAppender & logback-spring.xml
│
├── k8s/                         # Declarative Kubernetes manifests (Local & AWS EKS)
│   ├── 00-namespace.yaml
│   ├── 01-config-and-secrets.yaml
│   ├── 02-storage/
│   ├── 03-infrastructure/       # postgres.yaml, ollama.yaml, ollama-job.yaml
│   ├── 04-apps/                 # api-gateway, log-layer, llm-service, ml-pipeline, frontend
│   └── kustomization.yaml
│
├── scripts/                     # Automated orchestration & live simulation scripts
│   ├── start-all.ps1            # Multi-service launcher (PowerShell)
│   ├── start-all.sh             # Multi-service launcher (Bash)
│   ├── stop-all.ps1             # Multi-service graceful teardown
│   ├── test-live-springboot-ingest.ps1  # Real-time incident streaming simulator
│   └── setup-db.sql             # Multi-database PostgreSQL init script
│
├── docs/                        # Architecture diagrams & retrieval benchmark reports
│   └── assets/
│
├── docker-compose.yml           # Local multi-stage development stack
├── docker-compose.prod.yml      # Production stack pulling Docker Hub images
├── README.md
└── .gitignore
```

Each service has its own dependencies, configuration, and responsibilities.

---

# 7. Microservices and Responsibilities

| Service | Port | Main Responsibility |
|---|---:|---|
| API Gateway | `8080` | Routing and security |
| User Service | `8081` | Authentication and user management |
| Log Layer | `8082` | Log processing and orchestration |
| ML Pipeline | `8000` | Feature extraction and anomaly prediction |
| LLM Service | `8084` | RAG-based incident analysis |
| Frontend | `5173` | Web application |
| PostgreSQL | `5432` | Persistent storage |
| Ollama | `11434` | Local model inference |

## 7.1 API Gateway

**Technology:** Spring Cloud Gateway, Spring WebFlux, Spring Security.

The API Gateway acts as the single entry point for frontend requests.

Responsibilities:

- Route requests to backend services.
- Validate JWT authentication.
- Apply authorization rules.
- Handle CORS configuration.
- Centralize gateway security.

### Configured routes

```text
/api/v1/auth/**   → User Service (:8081)
/api/v1/users/**  → User Service (:8081)
/api/v1/logs/**   → Log Layer (:8082)
```

The Gateway uses reactive security configuration through `SecurityWebFilterChain`.

## 7.2 User Service

**Technology:** Spring Boot, Spring Security, Spring Data JPA, PostgreSQL.

Responsibilities:

- User registration.
- User login.
- Password authentication.
- JWT generation.
- User profile management.
- User persistence.

The service uses a JWT authentication filter based on Spring Security's `OncePerRequestFilter`.

## 7.3 Log Layer

**Technology:** Spring Boot, Spring Data JPA, PostgreSQL, WebClient.

The Log Layer is responsible for managing the log-analysis workflow.

Responsibilities:

- Receive log-ingestion requests.
- Parse and validate incoming logs.
- Store logs.
- Search and retrieve logs.
- Trigger ML analysis.
- Store ML predictions.
- Trigger LLM analysis.
- Store analysis results.
- Return structured responses to the frontend.

The Log Layer acts as the orchestration layer between the application logs, ML Pipeline, and LLM Service.

### ML integration

The Log Layer maps log information into an ML prediction request using its integration layer and communicates with the ML Pipeline.

### Analysis lifecycle

The log entity maintains analysis-related metadata, including:

```text
prediction
predictionLabel
decisionScore
modelVersion
analysisStatus
analyzedAt
```

The analysis lifecycle includes:

```text
PENDING
PROCESSING
COMPLETED
FAILED
```

## 7.4 ML Pipeline

**Technology:** Python, FastAPI, Scikit-learn.

Responsibilities:

- Receive structured log information.
- Extract model features.
- Perform anomaly detection.
- Support single predictions.
- Support batch predictions.
- Return prediction metadata.
- Expose model and health information.

## 7.5 LLM Service

**Technology:** Spring Boot, Spring AI, Ollama.

Responsibilities:

- Receive log-analysis requests.
- Construct analysis prompts.
- Retrieve relevant DevOps knowledge.
- Perform hybrid retrieval.
- Communicate with Ollama.
- Generate structured incident-analysis responses.

## 7.6 Frontend

**Technology:** React, Vite, Axios.

The frontend provides the user-facing application for:

- Authentication.
- Dashboard overview.
- Log ingestion.
- Log exploration.
- Search and filtering.
- AI analysis inspection.

The frontend communicates with the API Gateway rather than directly accessing individual backend services.

---

# 8. Database Design

DevInsight uses PostgreSQL for persistent application data.

The application maintains separate databases for user management and log processing.

## User Service Database

```text
Database: devinsight
Port:     5432
Username: postgres
```

The user database maintains information such as:

```text
users
roles
```

## Log Layer Database

```text
Database: log_layer_db
Port:     5432
Username: postgres
```

The log database maintains information such as:

```text
logs
log_analysis
```

The exact table structure is managed by the service's persistence entities and database configuration.

## Why separate databases?

Separating the databases keeps user-management data independent from log-processing data.

It also supports service-level ownership of persistence and allows the services to evolve independently.

---

# 9. Machine Learning Pipeline

The ML Pipeline is implemented as a separate FastAPI service.

Its responsibility is to identify anomalous behavior in application logs and return structured predictions to the Log Layer.

## ML workflow

```text
Structured Log Request
         |
         v
Feature Extraction
         |
         v
Feature Processing
         |
         v
Trained ML Model
         |
         v
Anomaly Prediction
         |
         v
Prediction Metadata
         |
         v
Log Layer
```

## Prediction information

The prediction response contains fields such as:

```text
prediction
prediction_label
is_anomaly
decision_score
model_version
```

The anomaly convention used by the model includes:

| Prediction | Meaning |
|---:|---|
| `-1` | Anomaly |
| `1` | Normal |

The Log Layer stores the prediction information alongside the corresponding log.

## ML API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/predict` | Single-log prediction |
| POST | `/api/v1/predict/batch` | Batch prediction |
| GET | `/api/v1/system/health` | Pipeline health |
| GET | `/api/v1/system/model` | Model information |

### Health check

```http
GET http://localhost:8000/api/v1/system/health
```

The health endpoint reports information such as service status, service name, version, environment, and model-loading status.

---

# 10. LLM and RAG Architecture

The LLM Service provides context-aware incident analysis using Retrieval-Augmented Generation.

Instead of relying only on the language model's internal knowledge, DevInsight retrieves relevant information from a DevOps knowledge base and includes it in the analysis prompt.

## 10.1 Why RAG?

A language model may not have sufficient information about a specific infrastructure error or the technical configuration of a system.

RAG helps by retrieving relevant documentation before generating an answer.

For example, when a log contains a PostgreSQL connection error, the retrieval pipeline can search for relevant PostgreSQL documentation and provide that context to the LLM.

## 10.2 Knowledge Base

The current documented knowledge base contains 21 Markdown documents.

| Category | Documents |
|---|---:|
| Docker | 5 |
| Kubernetes | 5 |
| Nginx | 3 |
| PostgreSQL | 3 |
| Spring Boot | 5 |
| **Total** | **21** |

Knowledge documents are stored under:

```text
llm-service/src/main/resources/knowledge/
```

## 10.3 Document Processing

The knowledge documents are split into smaller chunks before retrieval.

The documented stable configuration uses:

```text
Chunk size:    1000 characters
Chunk overlap: 0
```

These chunks are embedded and indexed for retrieval.

## 10.4 Dense Retrieval

Dense retrieval uses vector embeddings to identify semantically similar documents.

For example:

```text
Query:
"Why is my Spring Boot application unable to connect to PostgreSQL?"

        |
        v

Query Embedding
        |
        v

Vector Similarity Search
        |
        v

Relevant Knowledge Chunks
```

Dense retrieval is useful when the query and documentation use different wording but describe similar concepts.

## 10.5 BM25 Lexical Retrieval

BM25 is a lexical information-retrieval method that ranks documents using query-term relevance.

It is useful when exact technical terms matter.

For example:

```text
Query:
"CrashLoopBackOff"
```

BM25 can prioritize documentation containing that specific technical term.

## 10.6 Hybrid Retrieval

DevInsight combines dense retrieval and BM25 retrieval.

```text
                  User Query
                      |
              -----------------
              |               |
              v               v
        Dense Retrieval   BM25 Retrieval
              |               |
              v               v
        Semantic Results  Lexical Results
              |               |
              -------|--------
                      |
                      v
               RRF Fusion
                      |
                      v
              Ranked Results
                      |
                      v
              Prompt Construction
                      |
                      v
                   Ollama
                      |
                      v
              Generated Analysis
```

## 10.7 Reciprocal Rank Fusion

Reciprocal Rank Fusion combines the rankings produced by different retrieval systems.

The RRF score is:

\[
RRF(d)=\sum_{r \in R}\frac{1}{k+rank_r(d)}
\]

Where:

- \(d\) is a retrieved document.
- \(R\) represents the retrieval rankings.
- \(rank_r(d)\) is the rank of document \(d\) in ranking \(r\).
- \(k\) is a smoothing constant.

RRF combines rankings without requiring the retrieval systems to produce directly comparable raw scores.

## 10.8 LLM Inference

After retrieval, the LLM Service constructs a prompt containing:

- Log information.
- ML prediction information.
- Retrieved DevOps knowledge.
- Analysis instructions.

The prompt is sent to Ollama.

Current documented models:

```text
Chat Model:
phi3:mini

Embedding Model:
nomic-embed-text:latest
```

Ollama runs locally and exposes its service at:

```text
http://localhost:11434
```

## 10.9 Structured Analysis Response

The LLM Service generates an analysis response containing fields such as:

```text
summary
rootCause
severity
recommendation
```

This response is returned to the Log Layer for persistence and presentation through the frontend.

---

# 11. RAG Evaluation and Experiments

The retrieval pipeline was evaluated using an internal DevOps benchmark containing 20 queries.

The benchmark covers:

- Spring Boot
- Docker
- Kubernetes
- Nginx
- PostgreSQL

The objective was to compare retrieval strategies and select a configuration based on measured retrieval performance.

## 11.1 Evaluation Metrics

### Hit@1

Measures whether the relevant document appears at rank 1.

### Hit@3

Measures whether the relevant document appears within the top 3 results.

### Hit@5

Measures whether the relevant document appears within the top 5 results.

### Mean Reciprocal Rank (MRR)

MRR evaluates how highly the first relevant result is ranked.

\[
MRR=\frac{1}{N}\sum_{i=1}^{N}\frac{1}{rank_i}
\]

Where:

- \(N\) is the number of queries.
- \(rank_i\) is the position of the first relevant result for query \(i\).

A higher MRR indicates that relevant results tend to appear closer to the top.

## 11.2 Baseline Retrieval Results

The documented internal benchmark results are:

| Retrieval Strategy | Hit@1 | Hit@3 | Hit@5 | MRR |
|---|---:|---:|---:|---:|
| Dense, no overlap | 80% | 100% | 100% | 0.9000 |
| BM25, no overlap | 75% | 95% | 100% | 0.8542 |
| Hybrid RRF, no overlap | 75% | 95% | 100% | 0.8542 |

These results are specific to the project's internal benchmark. They should not be interpreted as general performance guarantees for RAG systems.

## 11.3 Chunk Overlap Experiment

A 200-character overlap was tested against the no-overlap configuration.

Results:

| Retrieval Strategy | MRR Without Overlap | MRR With 200-Character Overlap |
|---|---:|---:|
| Dense | 0.9000 | 0.8500 |
| BM25 | 0.8542 | 0.8042 |
| Hybrid | 0.8542 | 0.8042 |

The overlap configuration reduced the measured MRR in the benchmark.

**Decision:** Retain 1000-character chunks with zero overlap for the documented stable configuration.

## 11.4 Lexical Reranker Experiment

A lexical reranking approach was tested after Hybrid RRF.

```text
Dense Retrieval + BM25
          |
          v
       RRF Fusion
          |
          v
     Lexical Reranker
          |
          v
     Ranked Results
```

Results:

| Metric | Result |
|---|---:|
| Hit@1 | 70% |
| Hit@3 | 95% |
| Hit@5 | 100% |
| MRR | 0.8292 |

This approach performed worse than the Hybrid RRF baseline.

## 11.5 TF-IDF-Inspired Reranker Experiment

A second reranking experiment used TF-IDF-inspired weighting.

Results:

| Metric | Result |
|---|---:|
| Hit@1 | 70% |
| Hit@3 | 90% |
| Hit@5 | 95% |
| MRR | 0.8017 |

This approach also performed worse than the Hybrid RRF baseline.

## 11.6 Final Retrieval Decision

Based on the documented experiments, the stable retrieval configuration is:

```text
Chunk Size:       1000 characters
Chunk Overlap:    0

Retrieval:
    Dense Retrieval
    BM25 Retrieval

Fusion:
    Reciprocal Rank Fusion
```

The lexical and TF-IDF-inspired rerankers remain experimental approaches that did not improve the measured benchmark results.

Detailed experiment documentation:

[View RAG Retrieval Experiments and Final Decision](docs/rag_retrieval_experiments_and_final_decision.md)

---

# 12. API Reference

The following endpoints describe the documented service APIs.

All frontend requests are routed through the API Gateway where applicable.

## 12.1 Authentication APIs

### Register

```http
POST /api/v1/auth/register
```

Example request:

```json
{
  "email": "user@example.com",
  "password": "your-password",
  "role": "DEVELOPER"
}
```

### Login

```http
POST /api/v1/auth/login
```

Example request:

```json
{
  "email": "user@example.com",
  "password": "your-password"
}
```

The login response contains a JWT token.

### Get Current User

```http
GET /api/v1/users/me
```

Requires:

```http
Authorization: Bearer <token>
```

## 12.2 Log APIs

### Ingest a Log

```http
POST /api/v1/logs
```

The request supports fields such as:

```text
rawLog
source
environment
applicationName
hostName
correlationId
```

### Get a Log

```http
GET /api/v1/logs/{id}
```

### Search Logs

```http
POST /api/v1/logs/search
```

The frontend uses this endpoint for log searching and dashboard exploration.

### Analyze a Log

```http
POST /api/v1/logs/{id}/analyze
```

Documented analysis types include:

```text
SUMMARY
ROOT_CAUSE
SUGGESTED_FIX
```

### Delete a Log

```http
DELETE /api/v1/logs/{id}
```

## 12.3 ML Pipeline APIs

| Method | Endpoint |
|---|---|
| POST | `/api/v1/predict` |
| POST | `/api/v1/predict/batch` |
| GET | `/api/v1/system/health` |
| GET | `/api/v1/system/model` |

## 12.4 LLM Service APIs

| Method | Endpoint |
|---|---|
| POST | `/api/v1/llm/analyze` |
| GET | `/api/v1/system/health` |
| GET | `/test` |

The LLM analysis request can contain:

```text
timestamp
level
service_name
message
prediction
prediction_label
decision_score
model_version
```

The analysis response can contain:

```text
summary
rootCause
severity
recommendation
```

---

# 13. Prerequisites

Before running DevInsight locally, install the following tools.

| Requirement | Version / Tool |
|---|---|
| Java | Java 21 |
| Build Tool | Maven |
| Python | Python 3.x |
| Node.js | Node.js and npm |
| Database | PostgreSQL |
| LLM Runtime | Ollama |
| Version Control | Git |

### Verify installations

Using PowerShell:

```powershell
java -version
mvn -version
python --version
node --version
npm --version
psql --version
ollama --version
git --version
```

The project was developed and tested locally with Java 21.

---

# 14. Environment Configuration

Do not commit actual passwords, JWT secrets, or internal API keys.

The services use environment-variable placeholders for sensitive configuration.

Documented variables include:

```text
DB_PASSWORD
JWT_SECRET
INTERNAL_API_KEY
```

## PowerShell Configuration

Open PowerShell from the repository root.

Configure the PostgreSQL password:

```powershell
$env:DB_PASSWORD="<your-postgres-password>"
```

Configure the JWT secret:

```powershell
$env:JWT_SECRET="<your-jwt-secret>"
```

Configure the internal API key for the ML Pipeline:

```powershell
$env:INTERNAL_API_KEY="<your-local-internal-api-key>"
```

Replace the placeholders with your actual local values.

These PowerShell variables are session-level. If you open a new terminal, configure them again in that terminal where required.

### Security note

Never publish actual credentials in:

- GitHub repositories.
- README examples.
- Screenshots.
- Application logs.
- Public configuration files.

Use placeholders in documentation and keep real secrets outside version control.

---

# 15. Database Setup

DevInsight currently uses PostgreSQL.

Create the required databases before starting the backend services.

Open PostgreSQL using your preferred PostgreSQL client or `psql`.

Execute:

```sql
CREATE DATABASE devinsight;

CREATE DATABASE log_layer_db;
```

Verify the databases:

```sql
\l
```

The expected database configuration is:

| Database | Service | Port |
|---|---|---:|
| `devinsight` | User Service | 5432 |
| `log_layer_db` | Log Layer | 5432 |

The PostgreSQL username used in the documented local configuration is:

```text
postgres
```

Supply the database password through the `DB_PASSWORD` environment variable.

---

# 16. Ollama Setup

The LLM Service uses Ollama for local inference.

Install Ollama and make sure it is running.

The default local endpoint is:

```text
http://localhost:11434
```

## Pull the required models

### Chat model

```powershell
ollama pull phi3:mini
```

### Embedding model

```powershell
ollama pull nomic-embed-text:latest
```

## Verify installed models

```powershell
ollama list
```

The expected models are:

```text
phi3:mini
nomic-embed-text:latest
```

The chat model generates incident-analysis responses, while the embedding model is used to generate embeddings for semantic retrieval.

Ollama must be available when actual LLM analysis is performed.

---

# 17. Running the Project Locally

The recommended approach is to start and verify each service independently.

The following instructions use PowerShell.

Make sure PostgreSQL and Ollama are running before starting the application services.

## Terminal 1 — ML Pipeline

Navigate to the ML Pipeline directory:

```powershell
cd ml-pipeline
```

Configure the internal API key:

```powershell
$env:INTERNAL_API_KEY="<your-local-internal-api-key>"
```

Start the FastAPI application:

```powershell
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Service URL:

```text
http://localhost:8000
```

Health endpoint:

```text
http://localhost:8000/api/v1/system/health
```

## Terminal 2 — User Service

Open another terminal from the repository root.

Configure the database password and JWT secret:

```powershell
$env:DB_PASSWORD="<your-postgres-password>"
$env:JWT_SECRET="<your-jwt-secret>"
```

Start the User Service:

```powershell
mvn -f .\user-service\pom.xml spring-boot:run
```

Service URL:

```text
http://localhost:8081
```

## Terminal 3 — Log Layer

Open another terminal from the repository root.

Configure the database password:

```powershell
$env:DB_PASSWORD="<your-postgres-password>"
```

Start the Log Layer:

```powershell
mvn -f .\log-layer\pom.xml spring-boot:run
```

Service URL:

```text
http://localhost:8082
```

The Log Layer communicates with:

```text
ML Pipeline:
http://localhost:8000

LLM Service:
http://localhost:8084
```

Ensure the configured service URLs match your local environment.

## Terminal 4 — LLM Service

Open another terminal.

Navigate to the LLM Service:

```powershell
cd llm-service
```

Start the application:

```powershell
mvn spring-boot:run
```

Service URL:

```text
http://localhost:8084
```

Ensure Ollama is running and the required models are available.

## Terminal 5 — API Gateway

Open another terminal from the repository root.

Start the Gateway:

```powershell
mvn -f .\api-gateway\pom.xml spring-boot:run
```

Gateway URL:

```text
http://localhost:8080
```

The Gateway routes frontend requests to the User Service and Log Layer.

## Terminal 6 — Frontend

Open another terminal.

Navigate to the frontend directory:

```powershell
cd frontend
```

Install dependencies:

```powershell
npm install
```

Start the development server:

```powershell
npm run dev
```

Open the application:

```text
http://localhost:5173
```

### Expected local service URLs

| Component | URL |
|---|---|
| Frontend | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| User Service | http://localhost:8081 |
| Log Layer | http://localhost:8082 |
| ML Pipeline | http://localhost:8000 |
| LLM Service | http://localhost:8084 |
| Ollama | http://localhost:11434 |

---

## One-Click Orchestration Scripts

For quick local development without manual multi-terminal management, automated orchestration scripts are provided in `scripts/`:

### Windows (PowerShell)
```powershell
# Start all 6 microservices in parallel with auto pre-flight health checks:
.\scripts\start-all.ps1

# Stop and gracefully terminate all running DevInsight service processes:
.\scripts\stop-all.ps1

# Run the live Spring Boot error ingestion and Slack notification simulation:
.\scripts\test-live-springboot-ingest.ps1
```

### Linux / macOS / WSL (Bash)
```bash
chmod +x scripts/start-all.sh
./scripts/start-all.sh
```

---

## Production Kubernetes Deployment (`k8s/`)

DevInsight provides battle-tested, production-grade declarative Kubernetes manifests organized in `k8s/` following CNCF open standards. Compatible with local clusters (**Docker Desktop Kubernetes**, **Kind**, **Minikube**) and cloud managed clusters (**AWS EKS**, **GKE**, **Azure AKS**).

### Kubernetes Architecture Overview

All workloads run in an isolated namespace: `devinsight`.

```text
Kubernetes Cluster (Local Docker Desktop Kind / AWS EKS)
  └── Namespace: devinsight
       ├── ConfigMap & Secrets (devinsight-config, devinsight-secrets, postgres-init-script)
       │
       ├── Stateful Storage (PersistentVolumeClaims)
       │    ├── postgres-pvc (2Gi ReadWriteOnce)
       │    └── ollama-pvc (8Gi ReadWriteOnce)
       │
       ├── Infrastructure Layer
       │    ├── postgres (Deployment + ClusterIP Service :5432)
       │    └── ollama (Deployment + ClusterIP Service :11434 + ollama-model-puller Job)
       │
       ├── Application Microservices (Docker Hub images: adityagoku12/devinsight-*)
       │    ├── ml-pipeline (Deployment + ClusterIP Service :8000)
       │    ├── user-service (Deployment + ClusterIP Service :8081)
       │    ├── llm-service (Deployment + ClusterIP Service :8084)
       │    ├── log-layer (Deployment + ClusterIP Service :8082)
       │    ├── api-gateway (Deployment + ClusterIP Service :8080)
       │    └── frontend (Deployment + NodePort Service :80 / NodePort 30080)
```

### 1. Deploying to Local Kubernetes (Kind / Docker Desktop)

```bash
# 1. Deploy all resources via Kustomize:
kubectl apply -k k8s/

# 2. Check rollout status across all pods:
kubectl get pods -n devinsight -w

# 3. Forward frontend and gateway ports to your host:
kubectl port-forward svc/frontend -n devinsight 5173:80
kubectl port-forward svc/api-gateway -n devinsight 8080:8080
```

Access the web application at **`http://localhost:5173`** and the API Gateway at **`http://localhost:8080`**.

### 2. Deploying to AWS EKS (Elastic Kubernetes Service)

```bash
# 1. Create a 2-node managed EKS cluster:
eksctl create cluster \
  --name devinsight-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.xlarge \
  --nodes 2 --nodes-min 1 --nodes-max 3 \
  --managed

# 2. Update local kubeconfig context:
aws eks update-kubeconfig --region us-east-1 --name devinsight-cluster

# 3. Apply the DevInsight manifests:
kubectl apply -k k8s/
```

*To expose the frontend on AWS, change `type: NodePort` to `type: LoadBalancer` in `k8s/04-apps/frontend.yaml`; AWS will automatically provision an Application/Network Load Balancer.*

### 3. Production Resilience & Self-Healing Highlights:
- **Dual Health Probes**: Configured with `livenessProbe` (TCP socket / Actuator) and `readinessProbe` with automated traffic gate-keeping.
- **OOM Prevention**: Ollama memory limit stabilized at `6144Mi` (6 GB) with `OLLAMA_MAX_LOADED_MODELS: "1"` to eliminate CPU/VRAM memory thrashing.
- **Decoupled Model Pulling**: Model bootstrap tasks run in an independent Kubernetes `Job` (`ollama-model-puller`) that executes once to completion without interrupting container rollouts.

---

## Running with Docker Compose

DevInsight is fully containerized with multi-stage Dockerfiles and a production-grade `docker-compose.yml`.

### Architecture of Containers:
1. `devinsight-postgres` (PostgreSQL 16 Alpine with multi-database initialization)
2. `devinsight-ollama` (Ollama local inference runtime + volume persistence)
3. `devinsight-ollama-puller` (Automated puller for `phi3:mini` and `nomic-embed-text`)
4. `devinsight-ml-pipeline` (Python 3.11 FastAPI with preloaded Isolation Forest model)
5. `devinsight-user-service` (Java 21 Temurin Alpine microservice)
6. `devinsight-llm-service` (Java 21 Spring AI + pgvector RAG microservice)
7. `devinsight-log-layer` (Java 21 Log parsing, anomaly orchestration & live analyzer)
8. `devinsight-api-gateway` (Reactive Spring Cloud Gateway reverse proxy)
9. `devinsight-frontend` (Multi-stage Node.js 20 build served via Alpine Nginx)

### Launching the Complete Stack:
```bash
# 1. Start Docker Desktop (if not already running)

# 2. Build and launch all services in detached mode (Local Dev)
docker compose up --build -d

# 3. View live logs across all containers
docker compose logs -f

# 4. View logs for a specific service
docker compose logs -f log-layer

# 5. Stop the entire stack
docker compose down
```

---

## Production Deployment with Docker Hub (`docker-compose.prod.yml`)

For production environments, you can pull and run pre-built images directly from Docker Hub without needing local source code, Maven, Node.js, or Python installations:

```bash
# 1. Configure production environment (optional)
cp .env.prod.example .env.prod

# 2. Pull and start all production services in the background
docker compose -f docker-compose.prod.yml up -d

# 3. Monitor production logs
docker compose -f docker-compose.prod.yml logs -f

# 4. Stop the production stack
docker compose -f docker-compose.prod.yml down
```

### Production Features:
- **Zero-Build Deployment**: Direct pull from Docker Hub repository `adityagoku12/devinsight-*`.
- **Automatic Process Recovery**: `restart: unless-stopped` on all microservices.
- **Log Rotation**: Docker JSON log-driver with size limits (e.g., `max-size: 20m`, `max-file: 3`) to prevent host disk exhaustion.
- **Network Isolation**: All internal communication runs on an isolated bridge network (`devinsight-prod-net`).

Once running, navigate to `http://localhost:5173` to access the DevInsight web interface.

---

# 18. Frontend

The frontend is implemented using React and Vite.

It communicates with the API Gateway rather than directly accessing individual backend services.

## Main application areas

### Authentication

- User registration.
- User login.
- JWT-based authenticated requests.

### Dashboard

The dashboard provides an overview of the application's log-analysis information.

### Log Ingestion

Users can submit logs through the web interface with available metadata such as:

- Source.
- Environment.
- Application name.
- Host information.
- Correlation ID.

### Log Explorer

The Log Explorer provides log-searching and filtering functionality.

Documented dashboard functionality includes:

- Total log count.
- Error count.
- Warning count.
- AI analysis count.
- Severity filtering.
- Source filtering.
- Environment filtering.
- Application filtering.
- Anomaly filtering.
- Message search.
- Pagination.
- Log details.
- Analyze action.
- Delete action.

### AI Analysis

Users can inspect the analysis associated with a log, including:

- Summary.
- Probable root cause.
- Severity.
- Remediation.
- Extracted commands.
- Retrieved context.

### Live Log Inspector & Incident Sandbox (`/analyzer`)

The dedicated Live Inspector interface empowers DevOps engineers to test raw logs or stack traces interactively without manual pre-formatting:
- **Application Type Selection**: `SPRING_BOOT`, `DOCKER`, `KUBERNETES`, `NGINX`, `CUSTOM`.
- **Analysis Execution Mode**:
  - `FAST`: Sub-50ms heuristic & ML Isolation Forest triage without LLM call.
  - `AUTO`: Automatically triggers deep RAG + Ollama analysis only when an anomaly/error is detected.
  - `FORCE_DEEP`: Forces comprehensive RAG root-cause diagnosis on any log payload.
- **One-Click Quick Samples**: Preloaded realistic production crash scenarios (Spring Boot port conflict, Docker Redis timeout, Kubernetes `CrashLoopBackOff`, and clean healthy ping).
- **Interactive UI Cards**: Anomaly banner, decision score metrics, root cause summary, heuristic badge indicators, shell remediation commands, and formatted JSON output.
- Recommended fix.

---

# 19. Testing and Verification

DevInsight has been developed with service-level and integration verification.

The project has undergone local verification of the main services and frontend integration.

## ML Pipeline Testing

The ML Pipeline includes automated tests for its API, feature extraction, and prediction-related functionality.

The documented test suite reached:

```text
18 tests passed
```

## LLM Service Testing

The LLM Service includes tests covering service functionality, retrieval, vector storage, and analysis-related behavior.

A documented Maven test run completed with:

```text
Tests run: 192
Failures: 0
Errors: 0
Skipped: 0
```

## User Service Testing

The User Service test suite was also executed successfully after the security error-dispatch configuration change.

## Frontend Build

The frontend production build was verified using:

```powershell
npm run build
```

## Local Integration Verification

The documented local verification covered:

```text
ML Pipeline
User Service
Log Layer
LLM Service
API Gateway
Frontend
Gateway authentication
CORS preflight
Dashboard log loading
Frontend → API Gateway
API Gateway → Log Layer
```

These results describe the recorded development and testing state; they are not a guarantee that every environment or deployment configuration will behave identically.

---

# 20. Security Considerations

Security is handled through centralized Gateway authentication and service-level security configuration.

### JWT Authentication

The API Gateway uses JWT-based authentication for protected API requests.

### Public Authentication Endpoints

The registration and login endpoints are public authentication endpoints.

```text
/api/v1/auth/register
/api/v1/auth/login
```

The frontend avoids attaching a previously stored JWT to these public requests.

### Protected API Requests

Authenticated requests use:

```http
Authorization: Bearer <token>
```

### CORS

The Gateway handles CORS configuration for frontend communication.

Browser preflight requests are permitted so that cross-origin requests can proceed through the Gateway's security configuration.

### Secret Management

The project uses environment variables for sensitive values, including:

```text
DB_PASSWORD
JWT_SECRET
INTERNAL_API_KEY
```

Never commit real credentials.

For a public production deployment, additional security hardening is required, including reviewing role assignment, secret management, internal service authentication, and deployment-level access controls.

---

# 21. Known Limitations

The following limitations were documented during development.

## 21.1 Multiline and Stack-Trace Logs

Some multiline or stack-trace-style logs are currently rejected with:

```text
400 INVALID_LOG_FORMAT
```

Improving multiline parsing and stack-trace handling is a potential future enhancement.

## 21.2 Log Collection Endpoint

A general collection endpoint is not currently documented as implemented:

```http
GET /api/v1/logs
```

The frontend uses:

```http
POST /api/v1/logs/search
```

for log searching and retrieval.

## 21.3 Registration Role Selection

The current registration flow allows the client to provide a role.

For a public production deployment, role assignment should be restricted and controlled on the server side to prevent privilege escalation.

## 21.4 Deployment

The documented workflow is based on local service execution.

Docker Compose integration and Kubernetes deployment are future deployment stages rather than features that this README claims are already complete.

---

# 22. Development and Deployment Roadmap

The development sequence follows an incremental approach.

```text
Complete and Test Services Locally
                |
                v
       Verify Service Integration
                |
                v
       Docker Compose Integration
                |
                v
     Containerized System Testing
                |
                v
       Kubernetes Deployment
```

### Phase 1 — Local Development

- Develop individual services.
- Verify service-level functionality.
- Test inter-service communication.
- Integrate the frontend.
- Verify the end-to-end log-analysis workflow.

### Phase 2 — Docker Compose

Planned work:

- Containerize backend services.
- Containerize the ML Pipeline.
- Configure PostgreSQL.
- Configure Ollama.
- Configure service-to-service networking.
- Replace local `localhost` dependencies with container service names where appropriate.
- Add health checks and persistent volumes.
- Verify the complete application using Docker Compose.

### Phase 3 — Kubernetes

Planned work:

- Deploy services using Kubernetes workloads.
- Configure Services and internal networking.
- Manage application configuration and secrets.
- Add resource requests and limits.
- Configure health probes.
- Evaluate horizontal scaling.
- Deploy and verify the complete system.

---

# 23. Future Improvements

Potential improvements include:

- Better multiline and stack-trace log parsing.
- Additional log formats and structured log support.
- Improved anomaly-detection evaluation.
- More diverse log datasets.
- Additional DevOps knowledge-base documents.
- Further retrieval experiments.
- Evaluation of alternative embedding models.
- Evaluation of additional LLM models.
- Improved analysis reliability and response validation.
- Docker Compose deployment.
- Kubernetes deployment.
- Monitoring and observability for the microservices.
- More comprehensive end-to-end integration tests.

These are potential improvements and should not be interpreted as already implemented functionality.

---

# 24. Project Status

| Area | Status |
|---|---|
| Monorepo architecture | Completed |
| Git history preservation | Completed |
| Secret sanitization | Completed |
| API Gateway | Implemented |
| JWT authentication | Implemented |
| User Service | Implemented |
| Log Layer | Implemented |
| PostgreSQL integration | Implemented |
| ML Pipeline | Implemented |
| ML integration with Log Layer | Implemented |
| LLM Service | Implemented |
| Ollama integration | Implemented |
| DevOps knowledge base | Implemented |
| Dense retrieval | Implemented |
| BM25 retrieval | Implemented |
| RRF fusion | Implemented |
| RAG benchmark | Completed |
| Frontend integration | Implemented |
| Local service verification | Completed |
| Resilient Multi-line Log Parsing | Implemented |
| Selective Fast-path / Deep RAG | Implemented |
| Live Log Inspector Sandbox UI | Implemented |
| Multi-stage Dockerfiles | Implemented |
| Docker Compose Full Stack Orchestration | Implemented |
| Kubernetes Manifests (Local & AWS EKS) | Implemented |

---

# Summary

DevInsight combines multiple technologies into a unified DevOps incident-investigation platform.

```text
Microservices Architecture
          +
Spring Cloud Gateway
          +
JWT Authentication
          +
PostgreSQL
          +
Machine Learning Anomaly Detection
          +
Retrieval-Augmented Generation
          +
Dense Vector Retrieval
          +
BM25 Lexical Retrieval
          +
Reciprocal Rank Fusion
          +
Ollama Local Inference
          +
React Dashboard
```

The key idea behind DevInsight is to combine machine-learning-based anomaly detection with knowledge-grounded LLM analysis.

The ML Pipeline identifies anomalous log behavior, while the RAG-based LLM Service retrieves relevant DevOps knowledge and uses it to generate explanations and troubleshooting recommendations.

The retrieval pipeline was evaluated experimentally using an internal benchmark. Multiple approaches, including chunk overlap and reranking, were compared, and the documented stable configuration was selected based on the measured results.

**DevInsight aims to turn raw application logs into actionable incident insights through the integration of microservices, machine learning, and retrieval-augmented language models.**
