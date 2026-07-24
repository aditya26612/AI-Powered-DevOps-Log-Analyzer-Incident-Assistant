# 24-07-2026

Option A — Synchronous (Recommended)

    Save Log

    ↓

    Call ML

    ↓

    Return Prediction

    ↓

    Client gets result immediately

Response time:
~200–800 ms

Pros

    Simple
    Easy to test
    Great for interviews
    Suitable for most applications

    
---

Option B — Asynchronous

    Save Log

    ↓

    Return 202 Accepted

    ↓

    Background worker calls ML

    ↓

    Client polls later

Pros

    Highly scalable
    Better for huge workloads

Cons

    Requires queues/background processing
    More complexity

## Option A is for now ,we will use option B in future.

----

    Client
    │
    ▼
    API Gateway (JWT)
    │
    ├──────────────► User Service
    │
    └──────────────► Log Service
                            │
                            ▼
                    PostgreSQL
                            │
                            ▼
                    ML Service (FastAPI)
                            │
                            ▼
                    Trained Model

----

## Should every uploaded log be stored?

    Receive Log
    ↓

    Validate
    ↓

    Save
    ↓

    Analyze
    ↓

    Update Result

---

## Should ML results be stored?

I recommend storing them, because later you'll be able to:

    Build dashboards

    Search anomalies

    Retrain models

    Audit predictions

    Compare model versions

----

# I suggest we design the API so adding more models later won't require breaking changes.

    Log

    ↓

    Anomaly Detection

    ↓

    Severity Prediction

    ↓

    Root Cause Prediction

    ↓

    Recommendation Generator

----

# If the ML Service is down, what should happen?

I recommend:

    Save Log ✅

    ↓

    ML unavailable

    ↓

    Return

    analysisStatus = PENDING

---

# How should the Log Service call the ML Service?

Options:

    RestClient
    WebClient
    Feign Client

WebClient is also a good choice.

---

# Respone should be richer.

---
---

# DevInsight – Integration Planning Notes

## Current Status

The major architecture and design phases have been completed.

### ✅ Completed

- API Gateway architecture finalized.
- Log Layer architecture finalized.
- ML Service architecture finalized.
- REST API contract between Log Service and ML Service finalized.
- Log Entity updated to support future ML predictions.
- Database model prepared for ML integration.
- ML request/response contract finalized.
- Deployment roadmap finalized.

Deployment roadmap:

```
Local Development
        ↓
Docker Compose
        ↓
Frontend Integration
        ↓
Kubernetes Deployment
```

At this stage, the SDD is considered sufficient for beginning implementation. It will continue to evolve as
implementation progresses rather than attempting to design every future detail upfront.

---

# Next Phase – Spring Boot ↔ FastAPI Integration

The next objective is to integrate the Log Service with the ML Service using **Spring WebClient**.

The implementation will be completed incrementally through small milestones.

---

# Milestone 1 – Integration Infrastructure

**Goal:** Prepare the communication layer only.

No business logic should be implemented in this milestone.

### Package Structure

```
integration
│
├── client
│      └── MlServiceClient.java
│
├── config
│      └── WebClientConfig.java
│
├── dto
│      ├── MlPredictionRequest.java
│      └── MlPredictionResponse.java
│
└── exception
       └── MlServiceException.java
```

### Scope

- Configure WebClient.
- Create request DTO.
- Create response DTO.
- Create custom exception.
- Create ML client.

### Out of Scope

- Service layer integration.
- Controller changes.
- Repository changes.
- Entity updates.
- Business logic.
- Retry logic.
- Timeout handling.
- Circuit Breaker.
- Logging enhancements.

---

# Milestone 2 – Verify Communication

Before integrating with the Log Service, verify that communication between Spring Boot and FastAPI works correctly.

Communication flow:

```
Spring Boot
      │
      ▼
WebClient
      │
      ▼
FastAPI ML Service
```

Validation checklist:

- Send a prediction request successfully.
- Receive a valid response.
- Deserialize the response correctly.
- Verify error handling for unsuccessful responses.

No Log Service business logic should be modified during this milestone.

---

# Milestone 3 – Log Service Integration

After communication has been verified, integrate the ML client into the Log Service.

Target workflow:

```
Receive Log
      │
Validate
      │
Parse
      │
Persist Log
      │
Call ML Service
      │
Receive Prediction
      │
Update Log Entity
      │
Return Response
```

During this phase, the following entity fields become functional:

- prediction
- predictionLabel
- decisionScore
- modelVersion
- analysisStatus
- analyzedAt

---

# Milestone 4 – Integration Testing

Validate the complete integration using the following scenarios:

- Normal log prediction.
- Anomaly prediction.
- ML service unavailable.
- Timeout handling.
- Invalid response payload.
- Malformed JSON response.

---

# Development Strategy

Follow an incremental implementation approach.

For every milestone:

1. Design the milestone.
2. Implement only that milestone.
3. Test thoroughly.
4. Commit the changes.
5. Proceed to the next milestone.

Avoid implementing multiple milestones simultaneously.

---

# WebClient Decision

The project will use **Spring WebClient** for communication between the Log Service and the FastAPI ML Service.

Reasons:

- Recommended for modern Spring applications.
- Well suited for microservice communication.
- Easily extensible with retry, timeout, circuit breaker, metrics, and logging in future iterations.
- Compatible with the existing Spring Cloud Gateway architecture.
- Provides a clean foundation for future service-to-service communication.

---

# Current Project Progress

```
✔ User Service
        │
✔ API Gateway
        │
✔ Log Layer
        │
✔ ML Service
        │
✔ Database Model
        │
✔ ML API Contract
        │
✔ Log Entity Updated
        │
──────────────
🚀 Next:
Spring Boot ↔ FastAPI Integration
```

---

        # 📌 DevInsight - Log Layer
        # Milestone 1: ML Integration Infrastructure

        ---

        # 🎯 Objective

        Build a production-ready communication layer between the Spring Boot Log Layer and the FastAPI ML Pipeline without modifying any existing business logic.

        This milestone focuses only on creating the infrastructure required for communication.

        ---

        # 🏗 Architecture Designed

        ```
        Log Layer
        │
        ▼
        MlInferenceClient (Interface)
        │
        ▼
        WebClientMlInferenceClient
        │
        ▼
        Spring WebClient
        │
        ▼
        FastAPI ML Service
        ```

        Following the Dependency Inversion Principle (DIP), the service layer depends only on the interface and remains independent of the HTTP implementation.

        ---

        # 📁 Package Structure Added

        ```
        integration
        │
        ├── client
        │   ├── MlInferenceClient.java
        │   └── WebClientMlInferenceClient.java
        │
        ├── config
        │   ├── WebClientConfig.java
        │   └── MlServiceProperties.java
        │
        ├── dto
        │   ├── MlPredictionRequest.java
        │   └── MlPredictionResponse.java
        │
        └── exception
        └── MlServiceException.java
        ```

        ---

        # ✅ Dependencies Added

        Added Spring WebFlux dependency to enable WebClient.

        ```xml
        spring-boot-starter-webflux
        ```

        This allows the Log Layer to communicate with external REST services while keeping the rest of the application Spring MVC.

        ---

        # ⚙ WebClient Configuration

        Created:

        ```
        WebClientConfig
        ```

        Configured:

        - Dedicated `mlWebClient`
        - Base URL configured centrally
        - Uses `WebClient.Builder`
        - Registered `MlServiceProperties`

        Instead of concatenating URLs for every request, the base URL is configured once inside the WebClient.

        ---

        # ⚙ Configuration Properties

        Replaced multiple `@Value` annotations with a strongly typed configuration class.

        Created:

        ```
        MlServiceProperties
        ```

        Properties:

        ```properties
        ml.service.base-url=http://localhost:8000
        ml.service.predict-endpoint=/api/v1/predict
        ml.service.timeout=5s
        ```

        Advantages:

        - Centralized configuration
        - Easier maintenance
        - Type-safe properties
        - Easily extensible for future endpoints

        ---

        # 📦 Request DTO

        Created:

        ```
        MlPredictionRequest
        ```

        Purpose:

        Represents the request sent to the ML Service.

        Fields:

        - timestamp
        - level
        - serviceName
        - message

        Used:

        ```java
        @JsonProperty("service_name")
        ```

        to automatically convert Java camelCase into the snake_case format expected by FastAPI.

        ---

        # 📦 Response DTO

        Created:

        ```
        MlPredictionResponse
        ```

        Purpose:

        Represents the prediction returned by the ML Service.

        Fields:

        - prediction
        - predictionLabel
        - anomaly
        - decisionScore
        - modelVersion

        Used `@JsonProperty` wherever JSON naming differed from Java naming.

        ---

        # 🚨 Custom Exception

        Created:

        ```
        MlServiceException
        ```

        Purpose:

        Acts as the application's infrastructure exception for every ML communication failure.

        Instead of exposing Spring WebClient exceptions throughout the application, every communication error is converted into a single custom exception.

        ---

        # 🔌 Client Contract

        Created interface:

        ```
        MlInferenceClient
        ```

        Method:

        ```java
        MlPredictionResponse predict(MlPredictionRequest request);
        ```

        The service layer depends only on this interface.

        This allows future implementations without changing business logic.

        Possible future implementations:

        ```
        MlInferenceClient
                │
                ├── WebClientMlInferenceClient
                ├── GrpcMlInferenceClient
                ├── MockMlInferenceClient
                └── CachedMlInferenceClient
        ```

        ---

        # 🌐 HTTP Implementation

        Created:

        ```
        WebClientMlInferenceClient
        ```

        Responsibilities:

        - Send prediction requests
        - Deserialize responses
        - Handle HTTP errors
        - Handle network failures
        - Convert infrastructure exceptions
        - Apply request timeout

        Implemented using:

        ```
        WebClient
        ```

        instead of RestTemplate.

        ---

        # 🛡 Production Features Added

        Implemented:

        ✅ POST request

        ✅ Externalized endpoint configuration

        ✅ HTTP status handling

        ```
        .onStatus(...)
        ```

        ✅ Exception translation

        ```
        MlServiceException
        ```

        ✅ Request timeout

        ```
        .timeout(...)
        ```

        ✅ Logging

        ```
        @Slf4j
        ```

        This makes the communication layer production-ready before integrating it with business logic.

        ---

        # 🏛 Design Decisions

        ### Interface over Concrete Class

        Chose:

        ```
        MlInferenceClient
        ```

        instead of directly depending on the implementation.

        Reason:

        Provides a stable contract for future implementations without changing the service layer.

        ---

        ### ConfigurationProperties over @Value

        Chose:

        ```
        MlServiceProperties
        ```

        instead of scattered `@Value` annotations.

        Benefits:

        - Centralized configuration
        - Cleaner code
        - Better scalability

        ---

        ### Separate DTOs

        The Log entity is **never** sent directly to the ML service.

        Instead:

        ```
        Log
        │
        ▼
        MlPredictionRequest
        ```

        This keeps both services loosely coupled.

        ---

        ### WebClient

        Selected:

        ```
        WebClient
        ```

        Reasons:

        - Modern Spring HTTP client
        - Better timeout support
        - Cleaner API
        - Future reactive compatibility

        ---

        # ✅ Milestone 1 Outcome

        Successfully built the complete integration infrastructure between the Spring Boot Log Layer and the FastAPI ML Pipeline.

        At the end of this milestone, the application is capable of making production-ready HTTP requests to the ML service, but no business logic has been modified yet.

        ---

        # 🚀 Next Milestone

        Milestone 2: ML Service Integration

        Planned architecture:

        ```
        Controller
        │
        ▼
        LogService
        │
        ▼
        parseLog()
        │
        ▼
        Log Entity
        │
        ▼
        performMlAnalysis()
        │
        ▼
        MlPredictionMapper
        │
        ▼
        MlPredictionRequest
        │
        ▼
        MlInferenceClient
        │
        ▼
        FastAPI
        │
        ▼
        MlPredictionResponse
        │
        ▼
        Update Log Entity
        │
        ▼
        Save Log
        ```

        The goal of Milestone 2 is to integrate the infrastructure built in Milestone 1 into the existing log ingestion workflow while preserving the clean architecture of the Log Layer.



---

        # 📅 DevOps Analyzer Project Notes

        ## Milestone: Spring Boot ↔ ML Pipeline Integration Completed

        ---

        # 🎯 Objective

        Integrate the **Log Layer (Spring Boot)** with the **ML Pipeline (FastAPI)** so that every ingested log is automatically analyzed by the trained Isolation Forest model before being stored in the database.

        ---

        # 🏗️ Overall Architecture

        ```text
        Client
        │
        ▼
        LogController
        │
        ▼
        LogService
        │
        ├── Parse Log
        ├── Convert to Entity
        ├── Enrich Metadata
        ├── Call ML Service
        ├── Store ML Result
        └── Save to Database
                │
                ▼
        PostgreSQL
        ```

        ---

        # 📂 New Integration Package Structure

        Created a dedicated integration module to keep communication with the ML service isolated from business logic.

        ```text
        integration/
        │
        ├── client/
        │     ├── MlInferenceClient.java
        │     └── WebClientMlInferenceClient.java
        │
        ├── config/
        │     ├── MlServiceProperties.java
        │     └── WebClientConfig.java
        │
        ├── dto/
        │     ├── MlPredictionRequest.java
        │     └── MlPredictionResponse.java
        │
        └── exception/
        └── MlServiceException.java
        ```

        ### Purpose

        - Clean separation of concerns
        - Easier testing
        - Easier replacement of ML implementation in future
        - Keeps service layer independent of HTTP communication

        ---

        # 📦 Added Dependency

        Added Spring WebFlux to use **WebClient**.

        ```xml
        spring-boot-starter-webflux
        ```

        Reason:

        - Modern non-blocking HTTP client
        - Better than RestTemplate
        - Recommended by Spring

        ---

        # 🧩 Created MLPredictionMapper

        Created a new MapStruct mapper to convert the Log entity into the request expected by the ML service.

        ```text
        Log Entity
        │
        ▼
        MlPredictionMapper
        │
        ▼
        MlPredictionRequest
        ```

        Benefit:

        - Removes manual object mapping
        - Keeps service logic clean
        - Easier maintenance

        ---

        # 🔄 Updated LogService Flow

        Old flow:

        ```text
        Parse
        │
        ▼
        Save
        ```

        New flow:

        ```text
        Parse
        │
        ▼
        Map Entity
        │
        ▼
        Enrich Metadata
        │
        ▼
        Call ML Service
        │
        ▼
        Update Entity
        │
        ▼
        Save
        ```

        Implementation sequence:

        ```java
        ParsedLogData parsedLogData = parseLog(request);

        Log log = parsedLogMapper.toEntity(parsedLogData);

        enrichLogEntity(log, request);

        performMlAnalysis(log);

        Log savedLog = logRepository.save(log);

        return logMapper.toLogResponse(savedLog);
        ```

        ---

        # 🤖 ML Analysis Process

        Created helper method:

        ```java
        performMlAnalysis()
        ```

        Responsibilities:

        - Convert Log → ML Request
        - Invoke FastAPI using WebClient
        - Receive prediction response
        - Update entity with ML results
        - Set analysis status
        - Store analysis timestamp

        ---

        # 📊 Analysis Status Handling

        Used existing enum:

        ```text
        PENDING
        PROCESSING
        COMPLETED
        FAILED
        ```

        Logic:

        ### On Success

        - Save prediction
        - Save prediction label
        - Save decision score
        - Save model version
        - Set status → COMPLETED
        - Store analyzedAt timestamp

        ### On Failure

        - Do not reject log ingestion
        - Set status → FAILED
        - Store analyzedAt timestamp

        ---

        # ⚠️ Production Design Decision

        The Log Layer **must not fail** if the ML service is unavailable.

        Reason:

        A logging platform should continue accepting logs even if AI analysis is temporarily down.

        This improves:

        - Reliability
        - Fault tolerance
        - Availability

        ---

        # 🐛 Compilation Issue Encountered

        Error:

        ```text
        cannot find symbol

        method error(...)
        ```

        Cause:

        Method parameter:

        ```java
        performMlAnalysis(Log log)
        ```

        shadowed Lombok's logger variable (`log`).

        Solution:

        Renamed parameter to:

        ```java
        Log logEntity
        ```

        and added:

        ```java
        @Slf4j
        ```

        Result:

        ✅ Compilation successful

        ---

        # 🗄️ Database Changes

        New ML-related columns:

        ```text
        prediction
        prediction_label
        decision_score
        model_version
        analysis_status
        analyzed_at
        ```

        These are now automatically populated during log ingestion.

        ---

        # 🧪 FastAPI Verification

        Health endpoint:

        ```http
        GET /api/v1/system/health
        ```

        Verified:

        - Service UP
        - Model loaded
        - Version returned

        ---

        Prediction endpoint:

        ```http
        POST /api/v1/predict
        ```

        Successfully returned:

        - Prediction
        - Prediction Label
        - Decision Score
        - Model Version

        ---

        # 🧪 End-to-End Testing

        Used a valid Spring Boot log:

        ```text
        2026-07-25T02:15:30.123+05:30 ERROR 5020 --- [main] com.project.service.OrderService : NullPointerException while processing request
        ```

        Flow executed successfully:

        ```text
        Postman
        │
        ▼
        Spring Boot
        │
        ▼
        Parser
        │
        ▼
        Entity Mapping
        │
        ▼
        ML Service
        │
        ▼
        Database
        │
        ▼
        Response
        ```

        ---

        # 🗄️ Database Verification

        Verified saved values:

        ```text
        prediction        = 1
        predictionLabel   = Normal
        decisionScore     = 0.011477772298989386
        modelVersion      = 1.0.0
        analysisStatus    = COMPLETED
        analyzedAt        = Stored Successfully
        ```

        Confirmed:

        ✅ ML response persisted correctly.

        ---

        # 📤 Response DTO Enhancement

        Initially, the API response did not expose ML fields.

        Added to `LogResponse`:

        ```text
        prediction
        predictionLabel
        decisionScore
        modelVersion
        analysisStatus
        analyzedAt
        ```

        Since field names matched the entity, MapStruct mapped them automatically.

        No additional `@Mapping` annotations were required.

        ---

        # ✅ Final API Response

        Response now contains:

        ```json
        {
        "prediction": 1,
        "predictionLabel": "Normal",
        "decisionScore": 0.011477772298989386,
        "modelVersion": "1.0.0",
        "analysisStatus": "COMPLETED",
        "analyzedAt": "2026-07-25T02:35:14.5961524"
        }
        ```

        ---

        # 🏆 Final End-to-End Flow

        ```text
        Client (Postman)
                │
                ▼
        LogController
                │
                ▼
        LogService
                │
                ▼
        SpringBootParser
                │
                ▼
        ParsedLogMapper
                │
                ▼
        Enrich Metadata
                │
                ▼
        MlPredictionMapper
                │
                ▼
        WebClient
                │
                ▼
        FastAPI ML Pipeline
                │
                ▼
        Isolation Forest Model
                │
                ▼
        Prediction Response
                │
                ▼
        Update Log Entity
                │
                ▼
        PostgreSQL
                │
                ▼
        LogMapper
                │
                ▼
        LogResponse
                │
                ▼
        Client
        ```

        ---

        # ✅ Milestone Achievements

        ### Log Layer

        - ✅ Parser implementation
        - ✅ Entity mapping
        - ✅ Metadata enrichment
        - ✅ ML integration
        - ✅ Database persistence
        - ✅ Response mapping

        ---

        ### ML Pipeline

        - ✅ Model loading
        - ✅ Feature extraction
        - ✅ Prediction endpoint
        - ✅ Health endpoint

        ---

        ### Integration

        - ✅ Spring Boot ↔ FastAPI communication
        - ✅ WebClient configuration
        - ✅ DTO mapping
        - ✅ Prediction persistence
        - ✅ End-to-end testing completed

        ---

        # 📚 Key Learnings

        - Use **WebClient** for inter-service communication in modern Spring Boot applications.
        - Keep external service communication in a dedicated integration layer.
        - Use MapStruct to eliminate manual DTO conversions.
        - Logging systems should remain fault tolerant even if dependent services are unavailable.
        - Verify integrations at three levels:
        1. API Response
        2. Database Persistence
        3. End-to-End Workflow
        - Automatic field mapping in MapStruct works when entity and DTO field names match.

        ---

        # 🚀 Current Project Status

        ```text
        User Service          ✅ Completed
        API Gateway           ✅ Completed
        Log Layer             ✅ Completed
        ML Pipeline           ✅ Completed
        Spring ↔ FastAPI      ✅ Integrated
        End-to-End Testing    ✅ Successful
        ```

        ---

        # 🎯 Next Phase

        Proceed with integrating the **API Gateway** so that all client requests flow through a single entry point before reaching the Log Layer and other services.