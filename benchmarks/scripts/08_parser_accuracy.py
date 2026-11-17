"""
Benchmark 08: Polyglot Parser Accuracy
Sends real log samples from all 6 runtimes (Python, Node.js, Go, Spring Boot, K8s, DB)
and verifies the system correctly parses, persists, and classifies each format.
"""
import requests, json, os, time

API_KEY = "devinsight_internal_ml_key_secret_12345"
GATEWAY_URL = "http://localhost:8080/api/v1/logs"

# Real log samples per runtime — multi-line where applicable
PARSER_TEST_CASES = [
    {
        "source": "PYTHON",
        "environment": "PRODUCTION",
        "applicationName": "parser-bench-python",
        "hostName": "bench-py-1",
        "rawLog": """Traceback (most recent call last):
  File "/app/checkout.py", line 142, in process_transaction
    result = db.execute(query, params)
  File "/usr/local/lib/python3.11/asyncio/pool.py", line 488, in acquire
    raise PoolTimeoutError("connection pool exhausted after 5000ms")
asyncio.exceptions.PoolTimeoutError: connection pool exhausted after 5000ms""",
        "expected_level": "ERROR",
        "description": "Python multi-line traceback",
    },
    {
        "source": "NODEJS",
        "environment": "STAGING",
        "applicationName": "parser-bench-node",
        "hostName": "bench-node-1",
        "rawLog": """Error: connect ECONNREFUSED 127.0.0.1:6379
    at TCPConnectWrap.afterConnect [as oncomplete] (node:net:1278:16)
    at TCPConnectWrap.callbackTrampoline (node:internal/async_hooks:130:17)
[2026-11-15T10:00:00.000Z] FATAL Redis connection failed after 3 retries""",
        "expected_level": "ERROR",
        "description": "Node.js Winston error with stack trace",
    },
    {
        "source": "GO",
        "environment": "PRODUCTION",
        "applicationName": "parser-bench-go",
        "hostName": "bench-go-1",
        "rawLog": """{"time":"2026-11-15T10:00:00Z","level":"ERROR","msg":"Database connection failed","service":"payment-svc","error":"dial tcp: connection refused","goroutine":42,"stack":"goroutine 42 [running]:\\nruntime/debug.Stack(0xc000112000?)"}""",
        "expected_level": "ERROR",
        "description": "Go structured JSON log with Zap/Logrus",
    },
    {
        "source": "SPRING_BOOT",
        "environment": "PRODUCTION",
        "applicationName": "parser-bench-spring",
        "hostName": "bench-spring-1",
        "rawLog": """2026-11-15 10:00:00.123  FATAL 12345 --- [worker-thread-1] c.p.gateway.PaymentGatewayService        : Critical payment processing failure

java.lang.OutOfMemoryError: Java heap space
	at java.util.Arrays.copyOf(Arrays.java:3210) ~[?:?]
	at com.payment.gateway.TransactionBatcher.flush(TransactionBatcher.java:89) ~[app.jar:1.0.0]
	at com.payment.gateway.BatchProcessor.run(BatchProcessor.java:234) [app.jar:1.0.0]""",
        "expected_level": "FATAL",
        "description": "Spring Boot Logback format with Java stack trace",
    },
    {
        "source": "KUBERNETES",
        "environment": "PRODUCTION",
        "applicationName": "parser-bench-k8s",
        "hostName": "bench-k8s-1",
        "rawLog": """2026-11-15T10:00:00.000Z FATAL [container/payment-pod-7d4f9b-xk9pq] OOMKilled: container exceeded memory limit 512Mi
Kubernetes Event: Reason=OOMKilling Message="Memory cgroup out of memory: Kill process 2847 (java) score 1023 or sacrifice child"
Node: gke-prod-cluster-node-1  Namespace: production  Pod: payment-pod-7d4f9b-xk9pq""",
        "expected_level": "FATAL",
        "description": "Kubernetes/Docker Fluent Bit daemonset log",
    },
    {
        "source": "MYSQL",
        "environment": "PRODUCTION",
        "applicationName": "parser-bench-db",
        "hostName": "bench-db-1",
        "rawLog": """2026-11-15 10:00:00.000 UTC [25432] ERROR:  deadlock detected
2026-11-15 10:00:00.001 UTC [25432] DETAIL:  Process 25432 waits for ShareLock on transaction 7291; blocked by process 25433.
2026-11-15 10:00:00.001 UTC [25433] DETAIL:  Process 25433 waits for ShareLock on transaction 7290; blocked by process 25432.
2026-11-15 10:00:00.002 UTC [25432] HINT:  See server log for query details.""",
        "expected_level": "ERROR",
        "description": "PostgreSQL/MySQL deadlock log format",
    },
]

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 08: Polyglot Parser Accuracy")
    print(f"Test cases: {len(PARSER_TEST_CASES)} runtimes")
    print("=" * 60)

    parse_results = []
    success_count = 0
    fail_count = 0

    for tc in PARSER_TEST_CASES:
        print(f"\n[{tc['source']}] {tc['description']}")
        payload = {
            "rawLog": tc["rawLog"],
            "source": tc["source"],
            "environment": tc["environment"],
            "applicationName": tc["applicationName"],
            "hostName": tc["hostName"],
        }
        try:
            start = time.perf_counter()
            r = requests.post(
                GATEWAY_URL, json=payload,
                headers={"X-API-Key": API_KEY, "Content-Type": "application/json"},
                timeout=15
            )
            elapsed_ms = (time.perf_counter() - start) * 1000

            if r.status_code in (200, 201):
                data = r.json()
                log_id = data.get("id") or data.get("logId")
                actual_level = data.get("level") or data.get("logLevel")
                actual_source = data.get("source")
                parsed_ok = True
                level_match = (actual_level or "").upper() == tc["expected_level"].upper()
                source_match = (actual_source or "").upper() == tc["source"].upper()

                success_count += 1
                status = "✓ PASS" if (parsed_ok and level_match) else "~ PARTIAL"
                print(f"  {status} | ID={log_id} | level={actual_level} (expected={tc['expected_level']}) "
                      f"| source={actual_source} | {elapsed_ms:.0f}ms")

                parse_results.append({
                    "runtime": tc["source"],
                    "description": tc["description"],
                    "status": "PASS" if level_match else "PARTIAL",
                    "log_id": log_id,
                    "expected_level": tc["expected_level"],
                    "actual_level": actual_level,
                    "level_match": level_match,
                    "source_match": source_match,
                    "elapsed_ms": round(elapsed_ms, 1),
                    "http_status": r.status_code,
                })
            else:
                fail_count += 1
                print(f"  ✗ FAIL | HTTP {r.status_code}: {r.text[:200]}")
                parse_results.append({
                    "runtime": tc["source"],
                    "description": tc["description"],
                    "status": "FAIL",
                    "http_status": r.status_code,
                    "error": r.text[:200],
                })
        except Exception as e:
            fail_count += 1
            print(f"  ✗ ERROR: {e}")
            parse_results.append({
                "runtime": tc["source"],
                "description": tc["description"],
                "status": "ERROR",
                "error": str(e),
            })

    parse_success_rate = round(100 * success_count / len(PARSER_TEST_CASES), 1)
    level_matches = sum(1 for r in parse_results if r.get("level_match", False))
    level_accuracy = round(100 * level_matches / max(success_count, 1), 1)

    result = {
        "benchmark": "08_parser_accuracy",
        "total_runtimes": len(PARSER_TEST_CASES),
        "ingestion_success": success_count,
        "ingestion_failures": fail_count,
        "ingestion_success_rate_pct": parse_success_rate,
        "log_level_matches": level_matches,
        "log_level_accuracy_pct": level_accuracy,
        "runtimes_tested": ["PYTHON", "NODEJS", "GO", "SPRING_BOOT", "KUBERNETES", "MYSQL"],
        "individual_results": parse_results,
    }

    print(f"\n{'='*60}")
    print(f"Ingestion success: {success_count}/{len(PARSER_TEST_CASES)} ({parse_success_rate}%)")
    print(f"Log level accuracy: {level_matches}/{success_count} ({level_accuracy}%)")

    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/08_parser_accuracy.json", "w") as f:
        json.dump(result, f, indent=2)
    print("[OK] Results saved to benchmarks/results/08_parser_accuracy.json")

