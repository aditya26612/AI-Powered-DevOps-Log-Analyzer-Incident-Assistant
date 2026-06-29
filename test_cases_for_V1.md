# Testing Guide – Log Layer (Version 1)

## Purpose

This document describes how to verify every feature implemented in the Log Layer.

The project is developed incrementally.

Every feature must pass all tests before the next feature is implemented.

---

# Testing Strategy

Each feature must be tested in four stages.

```
Unit Test
      ↓
API Test
      ↓
Database Verification
      ↓
Regression Test
```

Only after all four stages pass should development continue.

---

# Current Version

```
Version 1

✓ POST /api/v1/logs
```

Remaining endpoints will be implemented incrementally.

---

# Test Environment

## Software

* Java 21
* Spring Boot 3.5.x
* PostgreSQL
* Maven
* Swagger UI / Postman
* IntelliJ IDEA

---

## Database

Database Name

```
log_layer_db
```

---

## Start Application

```
mvn clean compile

mvn spring-boot:run
```

Application should start successfully.

---

# Endpoint 1

## POST /api/v1/logs

Status

```
IMPLEMENTED
```

---

## Purpose

Ingest a raw log into the system.

---

## Expected Flow

```
Request

↓

Controller

↓

Service

↓

ParserFactory

↓

Selected Parser

↓

ParsedLogData

↓

ParsedLogMapper

↓

Log Entity

↓

Repository

↓

Database

↓

LogMapper

↓

Response
```

---

## Test Case 1  ✅ 

### Valid Spring Boot Log

Request

```json
{
  "rawLog": "2026-06-29T15:20:31.123+05:30 INFO 12345 --- [main] com.project.service.UserService : User created successfully",
  "source": "SPRING_BOOT",
  "environment": "DEVELOPMENT",
  "applicationName": "user-service",
  "hostName": "localhost"
}
```

Expected

HTTP

```
201 CREATED  ✅ 
```

Database
 
* One row inserted   ✅

Verify   ✅

* id generated
* correlationId generated
* level = INFO
* status = RECEIVED
* anomaly = false
* createdAt populated

---

## Test Case 2   ✅

Missing Raw Log   ✅

```json
{
  "rawLog": "",
  "source": "SPRING_BOOT",
  "environment": "DEVELOPMENT",
  "applicationName": "user-service"
}
```

Expected

```
400 BAD REQUEST  ✅
```

Verify   ✅

ErrorResponse returned

No row inserted.  ✅

---

## Test Case 3

Invalid Log Format ✅

```json
{
  "rawLog": "Hello World",
  "source": "SPRING_BOOT",
  "environment": "DEVELOPMENT",
  "applicationName": "user-service"
}
```

Expected

```
400 BAD REQUEST ✅
```

Verify

InvalidLogFormatException

---

## Test Case 4  ✅

Unsupported Source

```json
{
  "rawLog":"....",
  "source":"UNKNOWN"
}
```

Expected

```
400 BAD REQUEST    ✅
```

---
 

# TESTING GUIDE

## AI-Powered DevOps Log Analyzer - Log Layer (Version 1)

---

# Document Information

| Item         | Value               |
| ------------ | ------------------- |
| Module       | Log Layer           |
| Version      | V1.0                |
| Status       | Completed           |
| Last Updated | June 2026           |
| Author       | Aditya Singh Senger |

---

# Objective

This document defines the complete API testing strategy for the Log Layer.

The project follows **Incremental Development**.

Each feature must successfully pass all test cases before the next feature is implemented.

---

# Current Implemented API

| Endpoint          | Status      |
| ----------------- | ----------- |
| POST /api/v1/logs | ✅ Completed |

Future endpoints

* GET /api/v1/logs/{id}
* DELETE /api/v1/logs/{id}
* POST /api/v1/logs/search
* POST /api/v1/logs/batch
* POST /api/v1/logs/{id}/analyze

---

# Test Environment

## Software

* Java 21
* Spring Boot 3.5.x
* PostgreSQL
* Maven
* Swagger UI
* IntelliJ IDEA

---

## Database

```
log_layer_db
```

---

## Start Application

```bash
mvn clean compile

mvn spring-boot:run
```

---

# API Under Test

```
POST /api/v1/logs
```

---

# Expected Processing Flow

```
Client

    │

    ▼

LogController

    │

    ▼

LogService

    │

    ▼

ParserFactory

    │

    ▼

Selected Parser

    │

    ▼

ParsedLogData

    │

    ▼

ParsedLogMapper

    │

    ▼

Log Entity

    │

    ▼

Repository

    │

    ▼

PostgreSQL

    │

    ▼

LogMapper

    │

    ▼

LogResponse
```

---

# Spring Boot Parser Test Cases

---

## TC-001 Valid Spring Boot Log ✅

### Request

```json
{
  "rawLog": "2026-06-29T15:20:31.123+05:30 INFO 12345 --- [main] com.project.service.UserService : User created successfully",
  "source": "SPRING_BOOT",
  "environment": "DEVELOPMENT",
  "applicationName": "user-service",
  "hostName": "localhost"
}
```

### Expected

```
HTTP 201 CREATED
```

### Verify

* Database row inserted
* Correlation ID generated
* Timestamp parsed
* INFO level stored
* Thread parsed
* Logger parsed
* Message parsed
* Status = RECEIVED

---

## TC-002 Invalid Spring Boot Log  ✅

```json
{
  "rawLog":"Hello World",
  "source":"SPRING_BOOT",
  "environment":"DEVELOPMENT",
  "applicationName":"user-service"
}
```

Expected

```
400 BAD REQUEST
```

Exception

```
InvalidLogFormatException
```

---

## TC-003 WARN Log

Verify

```
Level = WARN
```

---

## TC-004 ERROR Log

Verify

```
Level = ERROR
```

---

# Docker Parser Test Cases

---

## TC-005 Valid Docker Log ✅

```json
{
  "rawLog":"2026-06-29T10:15:30.123456789Z Started container successfully",
  "source":"DOCKER",
  "environment":"DEVELOPMENT",
  "applicationName":"docker-service"
}
```

Expected

```
201 CREATED
```

Verify

* Timestamp
* Message

---

## TC-006 Invalid Docker Log ✅

```json
{
  "rawLog":"Docker Started",
  "source":"DOCKER",
  "environment":"DEVELOPMENT",
  "applicationName":"docker-service"
}
```

Expected

```
400 BAD REQUEST
```

---

# Kubernetes Parser Test Cases

---

## TC-007 Valid Kubernetes Log ✅

```json
{
  "rawLog":"2026-06-29T15:20:31.123456789Z stdout F Application started",
  "source":"KUBERNETES",
  "environment":"PRODUCTION",
  "applicationName":"user-service"
}
```

Expected

```
201 CREATED
```

Verify

* Stream
* Flag
* Timestamp
* Message

---

## TC-008 stderr Log

Verify

```
stream = stderr
```

---

## TC-009 Invalid Kubernetes Log  ✅

```json
{
  "rawLog":"Application Started",
  "source":"KUBERNETES",
  "environment":"PRODUCTION",
  "applicationName":"user-service"
}
```

Expected

```
400 BAD REQUEST
```

---

# Nginx Parser Test Cases ✅

---

## TC-010 Valid Nginx Log

```json
{
  "rawLog":"127.0.0.1 - - [29/Jun/2026:15:20:31 +0530] \"GET /api/users HTTP/1.1\" 200 512 \"-\" \"Mozilla/5.0\"",
  "source":"NGINX",
  "environment":"PRODUCTION",
  "applicationName":"nginx"
}
```

Expected

```
201 CREATED
```

Verify

* Client IP
* HTTP Method
* URL
* Protocol
* Status Code
* Response Size
* User Agent

---

## TC-011 Invalid Nginx Log  ✅

```json
{
  "rawLog":"GET /index.html",
  "source":"NGINX",
  "environment":"PRODUCTION",
  "applicationName":"nginx"
}
```

Expected

```
400 BAD REQUEST
```

---

# Validation Test Cases

---

## TC-012 Empty Raw Log

Expected

```
400 BAD REQUEST
```

---

## TC-013 Missing Raw Log

Expected

```
400 BAD REQUEST
```

---

## TC-014 Missing Application Name

Expected

```
400 BAD REQUEST
```

---

## TC-015 Missing Source

Expected

```
400 BAD REQUEST
```

---

## TC-016 Invalid Source Enum   ✅

```json
{
  "rawLog":"....",
  "source":"UNKNOWN",
  "environment":"DEVELOPMENT",
  "applicationName":"user-service"
}
```

Expected

```
400 BAD REQUEST
```

Reason

```
Invalid enum value
```

---

## TC-017 Missing Environment

Expected

```
400 BAD REQUEST
```

---

## TC-018 Invalid Environment Enum

Expected

```
400 BAD REQUEST
```

---

# Business Logic Tests

---

## TC-019 Client Correlation ID

Provide correlationId.

Verify

Database stores the same UUID.

---

## TC-020 Auto Generated Correlation ID

Do not provide correlationId.

Verify

UUID automatically generated.

---

## TC-021 createdAt

Verify

```
createdAt != null
```

---

## TC-022 Status

Verify

```
RECEIVED
```

---

## TC-023 Anomaly

Verify

```
false
```

---

## TC-024 Summary ✅

Verify

```
null
```

---

## TC-025 Suggested Fix

Verify

```
null
```

---

# Database Verification ✅

Run

```sql
SELECT *
FROM logs
ORDER BY id DESC;
```

Verify

* id generated
* correlation_id populated
* application_name correct
* service_name correct
* timestamp parsed
* level correct
* logger_name correct
* thread_name correct
* message correct
* source correct
* environment correct
* status = RECEIVED
* anomaly = false
* anomaly_score = 0.0
* created_at populated

---

# Error Response Verification ✅

Every failed request must return

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "...",
  "message": "...",
  "path": "/api/v1/logs"
}
```

No client validation error should return HTTP 500.

---

# Regression Testing Checklist

Run before implementing every new endpoint.

* Application starts successfully
* POST /api/v1/logs works
* All four parsers pass
* Database insert successful
* MapStruct mapping correct
* ParserFactory selects correct parser
* GlobalExceptionHandler returns proper responses
* No unexpected HTTP 500 errors

---

# Incremental Development Roadmap

## Phase 1 (Completed)

### Endpoint

```
POST /api/v1/logs
```

Regression Required

* POST /api/v1/logs

---

## Phase 2

### Endpoint

```
GET /api/v1/logs/{id}
```

Test Cases

* Existing ID
* Invalid ID
* Response mapping
* Database retrieval

Regression

* POST /api/v1/logs

---

## Phase 3

### Endpoint

```
DELETE /api/v1/logs/{id}
```

Test Cases

* Existing ID
* Invalid ID
* Already deleted ID

Regression

* POST /api/v1/logs
* GET /api/v1/logs/{id}

---

## Phase 4

### Endpoint

```
POST /api/v1/logs/search
```

Test Cases

* Pagination
* Sorting
* Filtering
* Empty result
* Invalid filter

Regression

* POST
* GET
* DELETE

---

## Phase 5

### Endpoint

```
POST /api/v1/logs/batch
```

Test Cases

* Single log
* Multiple logs
* Partial success
* Complete success
* Empty batch

Regression

All previous endpoints

---

## Phase 6

### Endpoint

```
POST /api/v1/logs/{id}/analyze
```

Test Cases

* Existing log
* Missing log
* AI response
* Error handling

Regression

Entire application

---

# Version 1 Exit Criteria

Version 1 is considered complete only if:

* All 25 API test cases pass
* All four parsers successfully ingest valid logs
* Invalid requests return HTTP 400 with ErrorResponse
* Database records match parsed values
* No unexpected HTTP 500 responses occur
* Maven build succeeds
* Application starts successfully
* Swagger endpoint is accessible
* Regression checklist passes

Only after satisfying all of the above should development proceed to Version 2.
