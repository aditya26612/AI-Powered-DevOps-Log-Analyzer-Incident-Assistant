"""
Benchmark 03: End-to-End Pipeline Round-Trip
Tests full pipeline: Ingest -> ML Detect -> LLM Diagnose -> Slack Dispatch
Uses the /integrations/test endpoint which runs the complete 4-step simulation.
"""
import requests, time, json, statistics, os

GATEWAY_URL = "http://localhost:8080"
API_KEY = "devinsight_internal_ml_key_secret_12345"
N = 20  # E2E tests are heavier, 20 runs is statistically meaningful

# Test payloads simulating real incidents
test_payloads = [
    {
        "rawLog": "2026-11-15 03:12:45.123 ERROR --- [worker-1] c.p.checkout.PaymentService : Critical unhandled exception Traceback (most recent call last): File '/app/checkout.py', line 142, in process_transaction db.com + pool.timeout=5.0 File '/usr/local/lib/python3.11/asyncio/pool.py', line 488, in acquire_new_connection PoolTimeoutError: connection pool exhausted after 5000ms",
        "source": "PYTHON",
        "environment": "PRODUCTION",
        "applicationName": "checkout-service",
        "hostName": "prod-worker-3",
    },
    {
        "rawLog": "java.lang.OutOfMemoryError: Java heap space at java.util.Arrays.copyOf(Arrays.java:3210) at com.payment.gateway.TransactionBatcher.flush(TransactionBatcher.java:89) FATAL: JVM heap exhausted, forcing process restart",
        "source": "SPRING_BOOT",
        "environment": "PRODUCTION",
        "applicationName": "payment-gateway",
        "hostName": "prod-jvm-1",
    },
    {
        "rawLog": "FATAL [2026-11-15 04:30:12] PostgreSQL connection pool exhausted: HikariPool-1 - Connection is not available, request timed out after 30000ms. Active connections: 50/50. Waiting queue: 150",
        "source": "SPRING_BOOT",
        "environment": "PRODUCTION",
        "applicationName": "user-service",
        "hostName": "db-proxy-2",
    },
]

def run_e2e_via_direct_ingest(token):
    """Ingest directly then check if pipeline ran — measures raw E2E wall clock"""
    latencies = []
    errors = 0
    pipeline_steps = {"ingested": 0, "ml_detected": 0, "llm_analyzed": 0}

    headers = {
        "X-API-Key": API_KEY,
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    for i in range(N):
        payload = test_payloads[i % len(test_payloads)].copy()
        try:
            start = time.perf_counter()
            r = requests.post(f"{GATEWAY_URL}/api/v1/logs", json=payload, headers=headers, timeout=15)
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code in (200, 201):
                latencies.append(elapsed_ms)
                data = r.json()
                pipeline_steps["ingested"] += 1
                if data.get("anomaly") is not None:
                    pipeline_steps["ml_detected"] += 1
            else:
                errors += 1
        except Exception as e:
            errors += 1
            print(f"  [ERROR] E2E run {i+1}: {e}")

    return latencies, errors, pipeline_steps

def run_e2e_via_integration_test(token):
    """Use the /integrations/test endpoint (complete 4-step simulation with timing)"""
    latencies = []
    errors = 0
    step_success = []

    # First, get integrations to find a valid service
    try:
        r = requests.get(f"{GATEWAY_URL}/api/v1/integrations",
                        headers={"Authorization": f"Bearer {token}"}, timeout=10)
        if r.status_code != 200:
            print(f"  [WARN] Could not list integrations: {r.status_code}")
            return latencies, errors, step_success

        integrations = r.json()
        if not integrations:
            print("  [WARN] No integrations registered. Skipping integration test endpoint.")
            return latencies, errors, step_success

        # Use first integration
        service_id = integrations[0].get("id") or integrations[0].get("applicationName")
        print(f"  Using integration: {service_id}")

    except Exception as e:
        print(f"  [ERROR] Cannot list integrations: {e}")
        return latencies, errors, step_success

    for i in range(min(5, N)):  # Only 5 runs via this endpoint (LLM-heavy)
        try:
            start = time.perf_counter()
            r = requests.post(
                f"{GATEWAY_URL}/api/v1/integrations/test",
                json={"integrationId": service_id} if isinstance(service_id, int) else {"applicationName": service_id},
                headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
                timeout=30
            )
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code == 200:
                latencies.append(elapsed_ms)
                data = r.json()
                step_success.append(data)
                print(f"  Run {i+1}: {elapsed_ms:.0f} ms | steps: {list(data.keys())[:4]}")
            else:
                errors += 1
                print(f"  [WARN] Run {i+1}: {r.status_code} - {r.text[:100]}")
        except Exception as e:
            errors += 1
            print(f"  [ERROR] Run {i+1}: {e}")

    return latencies, errors, step_success

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 03: End-to-End Pipeline Round-Trip")
    print("=" * 60)

    # Load token
    try:
        with open("benchmarks/results/token.txt") as f:
            token = f.read().strip()
        print(f"[OK] Token loaded")
    except:
        print("[ERROR] Run 00_get_token.py first")
        import sys; sys.exit(1)

    print("\n--- Part A: Direct Ingest E2E ---")
    latencies_a, errors_a, steps_a = run_e2e_via_direct_ingest(token)

    print("\n--- Part B: Integration Test Endpoint ---")
    latencies_b, errors_b, steps_b = run_e2e_via_integration_test(token)

    all_latencies = latencies_a + latencies_b
    if all_latencies:
        s = sorted(all_latencies)
        result = {
            "benchmark": "03_e2e_pipeline",
            "n_total": len(all_latencies),
            "n_errors": errors_a + errors_b,
            "direct_ingest": {
                "n": len(latencies_a),
                "p50_ms": round(statistics.median(latencies_a), 2) if latencies_a else None,
                "p95_ms": round(sorted(latencies_a)[int(0.95 * len(latencies_a))], 2) if latencies_a else None,
                "mean_ms": round(statistics.mean(latencies_a), 2) if latencies_a else None,
                "max_ms": round(max(latencies_a), 2) if latencies_a else None,
                "pipeline_steps": steps_a,
            },
            "integration_test": {
                "n": len(latencies_b),
                "p50_ms": round(statistics.median(latencies_b), 2) if latencies_b else None,
                "mean_ms": round(statistics.mean(latencies_b), 2) if latencies_b else None,
                "max_ms": round(max(latencies_b), 2) if latencies_b else None,
            },
            "combined_p50_ms": round(statistics.median(all_latencies), 2),
            "combined_p95_ms": round(s[int(0.95 * len(s))], 2),
            "combined_mean_ms": round(statistics.mean(all_latencies), 2),
        }
    else:
        result = {"benchmark": "03_e2e_pipeline", "error": "No successful runs"}

    print(f"\nCombined E2E Results:")
    if all_latencies:
        print(f"  Runs: {len(all_latencies)}  |  Errors: {errors_a + errors_b}")
        print(f"  p50: {result['combined_p50_ms']} ms  |  p95: {result['combined_p95_ms']} ms")
        print(f"  Mean: {result['combined_mean_ms']} ms")

    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/03_e2e_pipeline.json", "w") as f:
        json.dump(result, f, indent=2)
    print("[OK] Results saved to benchmarks/results/03_e2e_pipeline.json")

