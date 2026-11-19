"""
===============================================================================
DevInsight AI - Dataset Generator for Independent Evaluation & Diagnostic Probes
===============================================================================
Creates:
1. data/labeled_operational_logs.jsonl (Dataset B: Decoupled Independent Benchmark)
2. data/operational_challenge_scenarios.jsonl (Dataset C: Operational Diagnostic Probes)

Ground truth is explicitly decoupled from severity:
- Benign handled errors/warnings -> NORMAL
- Silent critical failures at INFO/DEBUG -> ANOMALOUS
- True operational incidents -> ANOMALOUS
- Standard healthy operations -> NORMAL
- Single-event insufficient context -> UNCERTAIN
===============================================================================
"""

import json
import uuid
from datetime import datetime, timezone

def generate_datasets():
    # -------------------------------------------------------------------------
    # Dataset B: Labeled Operational Benchmark (Decoupled from Severity)
    # -------------------------------------------------------------------------
    labeled_logs = [
        # 1. Healthy Standard Operations (INFO/DEBUG -> NORMAL)
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:00:01Z",
            "level": "INFO",
            "service_name": "api-gateway",
            "message": "HTTP GET /api/v1/health returned 200 in 4ms",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Routine health check probe succeeded with low latency",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:00:05Z",
            "level": "INFO",
            "service_name": "user-service",
            "message": "User login authenticated successfully for user_id=4821",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Successful standard user authentication event",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:00:10Z",
            "level": "INFO",
            "service_name": "payment-service",
            "message": "Order order_91201 captured amount=49.99 USD via Stripe",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Normal financial transaction capture confirmation",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:00:15Z",
            "level": "DEBUG",
            "service_name": "docker-daemon",
            "message": "Container stats collection completed for container=c831f2 in 12ms",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Routine container metrics poll within SLA",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:00:20Z",
            "level": "INFO",
            "service_name": "k8s-kubelet",
            "message": "Synced pod payment-service-7f89d-abc successfully",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Periodic pod reconciliation status normal",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        },

        # 2. Benign Handled Errors / Harmless Warnings (WARN/ERROR -> NORMAL)
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:00Z",
            "level": "WARN",
            "service_name": "user-service",
            "message": "Cache miss for user profile user_8819; loaded from primary PostgreSQL DB in 8ms",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Expected cache-aside pattern behavior; automatic fallback executed seamlessly",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 0.95,
            "ambiguity_flag": False,
            "category": "benign_warning"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:05Z",
            "level": "WARN",
            "service_name": "api-gateway",
            "message": "Client called deprecated route /v1/products, header Sunset: 2027-01-01 sent",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Standard API lifecycle deprecation notification; non-actionable operational warning",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "benign_warning"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:10Z",
            "level": "ERROR",
            "service_name": "api-gateway",
            "message": "Client request rejected: HTTP 404 Not Found for URI /favicon.ico from 192.168.1.5",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Handled standard client-side 404 error; no system degradation or incident",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 0.95,
            "ambiguity_flag": False,
            "category": "benign_error"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:15Z",
            "level": "ERROR",
            "service_name": "user-service",
            "message": "Registration validation failed: duplicate email 'jane@example.com' HTTP 409 Conflict",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Handled input domain validation rejection; expected business logic response",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "benign_error"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:20Z",
            "level": "WARN",
            "service_name": "docker-daemon",
            "message": "Disk usage at 64% on /var/lib/docker - below warning threshold 80%",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Informational capacity report below operational alert threshold",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "benign_warning"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:01:25Z",
            "level": "WARN",
            "service_name": "payment-service",
            "message": "Transient HTTP 503 from external webhook endpoint, retrying attempt 1 of 3",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Expected transient network glitch with active resilient retry handler",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 0.90,
            "ambiguity_flag": False,
            "category": "benign_warning"
        },

        # 3. Silent Critical Failures at INFO/DEBUG (INFO/DEBUG -> ANOMALOUS)
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:02:00Z",
            "level": "INFO",
            "service_name": "payment-service",
            "message": "CRITICAL: Payment transaction processing throughput dropped to 0 tx/min in last 5m",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Sudden total loss of transactional throughput indicates revenue outage",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:02:05Z",
            "level": "INFO",
            "service_name": "user-service",
            "message": "SECURITY ALERT: 250 consecutive failed logins detected from remote IP 203.0.113.42",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Credential stuffing / brute-force attack in progress logged as INFO",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:02:10Z",
            "level": "INFO",
            "service_name": "k8s-kubelet",
            "message": "Volume pvc-991f unmounted unexpectedly while 4 active writes were pending",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Abrupt disk dismount with pending IO implies severe data loss risk",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:02:15Z",
            "level": "DEBUG",
            "service_name": "payment-service",
            "message": "Deadlock detected between thread-41 (holding LockA) and thread-88 (holding LockB)",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Concurrency lock deadlock in worker thread pool logged at debug level",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:02:20Z",
            "level": "INFO",
            "service_name": "api-gateway",
            "message": "Circuit breaker OPEN for user-service; failing fast 100% of incoming requests",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Circuit breaker trip causing total service outage for user requests",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        },

        # 4. Genuine High-Severity Incidents (WARN/ERROR/FATAL -> ANOMALOUS)
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:00Z",
            "level": "FATAL",
            "service_name": "payment-service",
            "message": "java.lang.OutOfMemoryError: Java heap space; process aborting",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "JVM heap memory exhaustion causes immediate container crash",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:05Z",
            "level": "ERROR",
            "service_name": "payment-service",
            "message": "HikariPool-1 - Connection is not available, request timed out after 30000ms",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Database connection pool exhausted; queries stalling across cluster",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:10Z",
            "level": "ERROR",
            "service_name": "k8s-kubelet",
            "message": "Container user-service exited with status code 137 (OOMKilled)",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Linux cgroup kernel killer terminated container due to memory threshold breach",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:15Z",
            "level": "ERROR",
            "service_name": "api-gateway",
            "message": "Upstream service payment-service connection refused: dial tcp 10.0.4.12:8080: connect: connection refused",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Upstream microservice is down or unresponsive to TCP SYN packets",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:20Z",
            "level": "ERROR",
            "service_name": "user-service",
            "message": "org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for non-replication superuser connections",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "PostgreSQL max_connections limit exceeded; all user queries rejected",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:03:25Z",
            "level": "FATAL",
            "service_name": "docker-daemon",
            "message": "Kernel panic - not syncing: Fatal exception in interrupt; host rebooting",
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "OS kernel panic causing entire node infrastructure crash",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        },

        # 5. Ambiguous / Context-Dependent Cases (UNCERTAIN)
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:04:00Z",
            "level": "ERROR",
            "service_name": "api-gateway",
            "message": "HTTP 500 Internal Server Error returned to client IP 10.12.0.8",
            "ground_truth": "UNCERTAIN",
            "is_anomaly": None,
            "label_rationale": "An isolated 500 error may be an unhandled client request bug or major outage; single log line is insufficient",
            "label_source": "synthetic_scenario",
            "review_status": "needs_adjudication",
            "reviewer_id": None,
            "confidence": 0.50,
            "ambiguity_flag": True,
            "category": "ambiguous_probe"
        },
        {
            "log_id": str(uuid.uuid4()),
            "timestamp": "2026-11-19T10:04:05Z",
            "level": "WARN",
            "service_name": "user-service",
            "message": "Slow query detected: SELECT * FROM audit_logs took 1250ms",
            "ground_truth": "UNCERTAIN",
            "is_anomaly": None,
            "label_rationale": "1.25s query on audit table could be normal background batch or query index degradation",
            "label_source": "synthetic_scenario",
            "review_status": "needs_adjudication",
            "reviewer_id": None,
            "confidence": 0.60,
            "ambiguity_flag": True,
            "category": "ambiguous_probe"
        }
    ]

    # Expand Dataset B to 200 realistic, operationally annotated entries by combining
    # realistic log templates with diverse services and parameter variations
    expanded_labeled_logs = list(labeled_logs)
    services = ["api-gateway", "user-service", "payment-service", "order-service", "inventory-service", "docker-daemon", "k8s-kubelet", "auth-service"]

    # Add healthy variations (NORMAL)
    for i in range(1, 100):
        srv = services[i % len(services)]
        ts = f"2026-11-19T{10 + (i // 60):02d}:{i % 60:02d}:{i % 59:02d}Z"
        expanded_labeled_logs.append({
            "log_id": str(uuid.uuid4()),
            "timestamp": ts,
            "level": "INFO",
            "service_name": srv,
            "message": f"Processed request req_{i*103} in {10 + (i % 25)}ms status=200",
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Standard HTTP request handling within SLA",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "healthy_operation"
        })

    # Add benign handled warnings and errors (NORMAL)
    for i in range(1, 40):
        srv = services[i % len(services)]
        ts = f"2026-11-19T11:{i % 60:02d}:{i % 59:02d}Z"
        lvl = "WARN" if i % 2 == 0 else "ERROR"
        msgs = [
            f"Handled 404 resource not found: /api/v1/items/sku_{i*91} by client",
            f"Cache entry expired for key session:{i*11}; re-fetched from database",
            f"User input validation error: field 'zipcode' invalid format value='{i*100}'",
            f"Handled rate limit: client id={i*3} exceeded tier 1 limit, returning 429 Retry-After: 60"
        ]
        expanded_labeled_logs.append({
            "log_id": str(uuid.uuid4()),
            "timestamp": ts,
            "level": lvl,
            "service_name": srv,
            "message": msgs[i % len(msgs)],
            "ground_truth": "NORMAL",
            "is_anomaly": False,
            "label_rationale": "Benign handled error or standard business constraint; no operational incident",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 0.95,
            "ambiguity_flag": False,
            "category": "benign_error" if lvl == "ERROR" else "benign_warning"
        })

    # Add silent critical incidents (ANOMALOUS at INFO/DEBUG)
    for i in range(1, 25):
        srv = services[i % len(services)]
        ts = f"2026-11-19T12:{i % 60:02d}:{i % 59:02d}Z"
        msgs = [
            f"CRITICAL STATE: Worker thread pool deadlock detected in {srv}; 0 tasks completed in last 10m",
            f"ALERT: Transaction processing halted abruptly after block {1000 + i}",
            f"Brute force signature: 500 failed login attempts from subnet 198.51.100.{i}",
            f"Data pipeline stalled: 100000 records queued without consumer acknowledgment"
        ]
        expanded_labeled_logs.append({
            "log_id": str(uuid.uuid4()),
            "timestamp": ts,
            "level": "INFO",
            "service_name": srv,
            "message": msgs[i % len(msgs)],
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Critical business/system failure or security incident logged at INFO level",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "silent_info_anomaly"
        })

    # Add genuine high-severity incidents (ANOMALOUS)
    for i in range(1, 30):
        srv = services[i % len(services)]
        ts = f"2026-11-19T13:{i % 60:02d}:{i % 59:02d}Z"
        lvl = "ERROR" if i % 3 != 0 else "FATAL"
        msgs = [
            f"HikariPool connection timeout: unable to borrow connection after 30000ms from postgres-cluster",
            f"java.lang.OutOfMemoryError: Metaspace exhausted; JVM terminating",
            f"Pod {srv}-pod-{i} entered CrashLoopBackOff: back-off 5m0s restarting failed container",
            f"Disk space full: write failed to /var/log/data (No space left on device errno=28)",
            f"Connection refused to Redis cluster redis-sentinel:26379; session cache unavailable"
        ]
        expanded_labeled_logs.append({
            "log_id": str(uuid.uuid4()),
            "timestamp": ts,
            "level": lvl,
            "service_name": srv,
            "message": msgs[i % len(msgs)],
            "ground_truth": "ANOMALOUS",
            "is_anomaly": True,
            "label_rationale": "Verified operational failure causing service degradation or outage",
            "label_source": "synthetic_scenario",
            "review_status": "not_human_verified",
            "reviewer_id": None,
            "confidence": 1.0,
            "ambiguity_flag": False,
            "category": "operational_incident"
        })

    with open("data/labeled_operational_logs.jsonl", "w", encoding="utf-8") as f:
        for entry in expanded_labeled_logs:
            f.write(json.dumps(entry) + "\n")

    print(f"Generated data/labeled_operational_logs.jsonl with {len(expanded_labeled_logs)} entries.")

    # -------------------------------------------------------------------------
    # Dataset C: Operational Challenge Scenarios (Diagnostic Probe Suite)
    # -------------------------------------------------------------------------
    challenge_probes = [
        # Category 1: Database Failures
        {"name": "PostgreSQL Connection Refused", "category": "database_failures", "level": "ERROR", "service": "payment-service", "msg": "org.postgresql.util.PSQLException: Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.", "expected": "ANOMALY", "why": "Database daemon is offline or unreachable"},
        {"name": "Hikari Pool Exhaustion", "category": "database_failures", "level": "ERROR", "service": "payment-service", "msg": "HikariPool-1 - Connection is not available, request timed out after 30002ms (active=50, idle=0, waiting=124)", "expected": "ANOMALY", "why": "Connection starvation prevents all new queries"},
        {"name": "Database Deadlock", "category": "database_failures", "level": "ERROR", "service": "order-service", "msg": "org.postgresql.util.PSQLException: ERROR: deadlock detected between process 14210 and process 14215", "expected": "ANOMALY", "why": "Concurrent transactions blocking each other"},
        {"name": "Slow Query Warning", "category": "database_failures", "level": "WARN", "service": "user-service", "msg": "Slow query alert: SELECT * FROM user_activity WHERE created_at > NOW() - INTERVAL '30 days' took 8540ms", "expected": "ANOMALY", "why": "Extreme query latency indicating missing index or table lock"},
        {"name": "Transaction Aborted Rollback", "category": "database_failures", "level": "ERROR", "service": "payment-service", "msg": "Transaction marked rollback-only: could not commit JPA transaction; nested exception is javax.persistence.RollbackException", "expected": "ANOMALY", "why": "Business transaction failed and was forcibly rolled back"},
        {"name": "PostgreSQL Recovery in Progress", "category": "database_failures", "level": "WARN", "service": "payment-service", "msg": "FATAL: the database system is starting up / in recovery mode; queries not accepted", "expected": "ANOMALY", "why": "Database in crash recovery state"},
        {"name": "Expected Transient Connection Retry", "category": "database_failures", "level": "WARN", "service": "user-service", "msg": "Temporary socket read timeout, retrying query (attempt 1/3) - recovered in 40ms", "expected": "NORMAL", "why": "Resilient client recovered automatically without user impact"},

        # Category 2: Distributed Systems
        {"name": "Upstream Service Connection Refused", "category": "distributed_systems", "level": "ERROR", "service": "api-gateway", "msg": "Upstream service user-service unavailable: dial tcp 10.0.1.20:8080: connect: connection refused", "expected": "ANOMALY", "why": "Core upstream dependency down"},
        {"name": "Circuit Breaker Tripped OPEN", "category": "distributed_systems", "level": "ERROR", "service": "api-gateway", "msg": "Resilience4j circuit breaker 'order-service' transitioned from HALF_OPEN to OPEN (failure rate: 85%)", "expected": "ANOMALY", "why": "Automated protection tripped due to continuous downstream failures"},
        {"name": "Gateway Timeout Burst", "category": "distributed_systems", "level": "ERROR", "service": "api-gateway", "msg": "HTTP 504 Gateway Timeout while waiting for payment-service after 60000ms", "expected": "ANOMALY", "why": "Downstream timeout causing cascading gateway backpressure"},
        {"name": "Connection Reset By Peer", "category": "distributed_systems", "level": "ERROR", "service": "user-service", "msg": "java.io.IOException: Connection reset by peer during TLS handshake with auth-server", "expected": "ANOMALY", "why": "Remote peer forcibly aborted connection"},
        {"name": "Consul Service Discovery Lost", "category": "distributed_systems", "level": "ERROR", "service": "api-gateway", "msg": "ServiceDiscoveryException: No healthy instances found for service 'inventory-service' in Consul registry", "expected": "ANOMALY", "why": "Total cluster discovery failure for inventory service"},
        {"name": "Intermittent Retry Success", "category": "distributed_systems", "level": "WARN", "service": "payment-service", "msg": "Webhook delivery to partner returned 502, retry attempt 2 succeeded with HTTP 200", "expected": "NORMAL", "why": "Transient failure resolved cleanly on retry"},
        {"name": "Circuit Breaker Closed After Recovery", "category": "distributed_systems", "level": "INFO", "service": "api-gateway", "msg": "Resilience4j circuit breaker 'order-service' transitioned from HALF_OPEN to CLOSED (healthy)", "expected": "NORMAL", "why": "System recovered to normal operational health"},

        # Category 3: Infrastructure
        {"name": "Kubernetes OOMKilled", "category": "infrastructure", "level": "FATAL", "service": "k8s-kubelet", "msg": "Pod payment-service-59bc7f container payment terminated exit_code=137 reason=OOMKilled", "expected": "ANOMALY", "why": "Operating system killed container due to cgroup limit breach"},
        {"name": "Pod CrashLoopBackOff", "category": "infrastructure", "level": "ERROR", "service": "k8s-kubelet", "msg": "Back-off 5m0s restarting failed container user-service in pod user-service-6d8b9 (restart_count=8)", "expected": "ANOMALY", "why": "Application continuously failing on startup"},
        {"name": "Container Eviction Disk Pressure", "category": "infrastructure", "level": "WARN", "service": "k8s-kubelet", "msg": "Node node-worker-04 status condition DiskPressure is true; initiating pod eviction", "expected": "ANOMALY", "why": "Host disk saturation triggering pod evictions"},
        {"name": "Disk Space Exhaustion", "category": "infrastructure", "level": "FATAL", "service": "docker-daemon", "msg": "write /var/lib/docker/overlay2/merged/log.txt: no space left on device", "expected": "ANOMALY", "why": "Root storage completely filled"},
        {"name": "High Memory Pressure", "category": "infrastructure", "level": "WARN", "service": "docker-daemon", "msg": "Host memory usage at 96.8% (available: 128MB); swapping heavily", "expected": "ANOMALY", "why": "Host entering severe memory thrashing"},
        {"name": "Network Interface Dropped Packets", "category": "infrastructure", "level": "WARN", "service": "k8s-kubelet", "msg": "Network eth0 packet drop rate exceeded 15% in last 60 seconds", "expected": "ANOMALY", "why": "Severe network interface saturation"},
        {"name": "CPU Throttling Notice", "category": "infrastructure", "level": "WARN", "service": "docker-daemon", "msg": "Container user-service throttled for 4200ms out of 10000ms CPU period", "expected": "ANOMALY", "why": "Container hitting CPU quotas causing user latency"},

        # Category 4: Security and Authentication
        {"name": "Brute Force Authentication Burst", "category": "security_and_auth", "level": "INFO", "service": "user-service", "msg": "Failed authentication attempt 50/50 for user admin from IP 192.168.1.100", "expected": "ANOMALY", "why": "Automated dictionary attack logged as INFO"},
        {"name": "Suspicious Token Reuse", "category": "security_and_auth", "level": "WARN", "service": "api-gateway", "msg": "SECURITY: Revoked refresh token presented from new geographic region (US -> RU)", "expected": "ANOMALY", "why": "Stolen credentials / token replay attack"},
        {"name": "Single Invalid Password (Handled)", "category": "security_and_auth", "level": "WARN", "service": "user-service", "msg": "User alice@example.com provided invalid password from web UI; attempt 1 of 5", "expected": "NORMAL", "why": "Expected human typo during routine authentication"},
        {"name": "Harmless Forbidden 403", "category": "security_and_auth", "level": "WARN", "service": "api-gateway", "msg": "Access denied: user_101 tried to access /api/v1/admin/billing without ROLE_ADMIN", "expected": "NORMAL", "why": "Properly enforced authorization boundary"},
        {"name": "Privilege Escalation Attempt", "category": "security_and_auth", "level": "ERROR", "service": "user-service", "msg": "SECURITY AUDIT: User guest attempted to invoke /api/v1/internal/grantSuperuser", "expected": "ANOMALY", "why": "Unauthorized administrative endpoint probing"},

        # Category 5: Application Behavior
        {"name": "Normal Startup", "category": "application_behavior", "level": "INFO", "service": "user-service", "msg": "Started UserApplication in 2.341 seconds (process running for 2.812)", "expected": "NORMAL", "why": "Clean Spring Boot microservice initialization"},
        {"name": "Graceful Shutdown", "category": "application_behavior", "level": "INFO", "service": "payment-service", "msg": "Graceful shutdown complete: active connections drained, Netty stopped", "expected": "NORMAL", "why": "Clean application lifecycle shutdown"},
        {"name": "Payment Processing Halted (INFO)", "category": "application_behavior", "level": "INFO", "service": "payment-service", "msg": "Payment processing rate dropped to 0 transactions per minute in last 5m", "expected": "ANOMALY", "why": "Total business transaction stall reported at INFO level"},
        {"name": "Uncaught Runtime NullPointer", "category": "application_behavior", "level": "ERROR", "service": "order-service", "msg": "java.lang.NullPointerException: Cannot invoke 'Order.getItems()' because 'order' is null at OrderService.checkout(OrderService.java:142)", "expected": "ANOMALY", "why": "Unhandled code exception crashing user request"},
        {"name": "Handled Business Validation", "category": "application_behavior", "level": "ERROR", "service": "user-service", "msg": "User registration rejected: email already registered user=bob@example.com", "expected": "NORMAL", "why": "Handled duplicate email validation in controller"},
        {"name": "Deprecation Notice", "category": "application_behavior", "level": "WARN", "service": "api-gateway", "msg": "Deprecated API version v1 called, client should upgrade to v2", "expected": "NORMAL", "why": "Harmless migration deprecation log"},
        {"name": "Unexpected Transaction Desync", "category": "application_behavior", "level": "FATAL", "service": "payment-service", "msg": "Ledger state corruption: debit total 1420.00 does not match credit total 1390.00 in batch 994", "expected": "ANOMALY", "why": "Financial integrity breach requiring immediate human intervention"},

        # Category 6: Adversarial and Robustness Tests
        {"name": "Completely Unknown Service Name", "category": "adversarial_and_robustness", "level": "INFO", "service": "quantum-ledger-daemon-v9", "msg": "Cluster synchronization initialized across 12 nodes", "expected": "NORMAL", "why": "Healthy event from an unseen microservice should not trigger false alarm"},
        {"name": "Extreme Long Message (Stack Trace)", "category": "adversarial_and_robustness", "level": "ERROR", "service": "user-service", "msg": "Exception in thread 'main' " + ("\n\tat com.project.service.Worker.run(Worker.java:42)" * 30), "expected": "ANOMALY", "why": "Deep multiline Java stacktrace should parse without crash"},
        {"name": "Empty Message Payload", "category": "adversarial_and_robustness", "level": "INFO", "service": "api-gateway", "msg": "   ", "expected": "NORMAL", "why": "Whitespace-only message must not crash the feature extractor"},
        {"name": "Unicode / Injection Characters", "category": "adversarial_and_robustness", "level": "INFO", "service": "user-service", "msg": "User entered nickname: ¯\\_(ツ)_/¯ <script>alert(1)</script> 🚀", "expected": "NORMAL", "why": "Non-ASCII and injection payloads must be safely handled"},
        {"name": "Off-Hour Weekend Log", "category": "adversarial_and_robustness", "level": "INFO", "service": "user-service", "msg": "Scheduled Sunday maintenance complete: 0 tables pruned", "expected": "NORMAL", "why": "Temporal shift should not trigger false anomaly alerts"},
        {"name": "Contradictory ERROR in INFO log", "category": "adversarial_and_robustness", "level": "INFO", "service": "user-service", "msg": "Processed log message stating 'error code 0: success'", "expected": "NORMAL", "why": "The word error was used in a success context"}
    ]

    with open("data/operational_challenge_scenarios.jsonl", "w", encoding="utf-8") as f:
        for p in challenge_probes:
            f.write(json.dumps(p) + "\n")

    print(f"Generated data/operational_challenge_scenarios.jsonl with {len(challenge_probes)} diagnostic probes.")

if __name__ == "__main__":
    generate_datasets()
