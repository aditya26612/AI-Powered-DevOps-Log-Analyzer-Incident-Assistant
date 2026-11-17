"""
Benchmark 01: Log Ingestion Latency (p50 / p95 / p99)
Sends 100 real log payloads to the API Gateway and measures response time distribution.
"""
import requests, time, json, statistics, os

API_KEY = "devinsight_internal_ml_key_secret_12345"
GATEWAY_URL = "http://localhost:8080/api/v1/logs"
LOG_LAYER_URL = "http://localhost:8082/api/v1/logs"
N = 100

sample_logs = [
    "2026-11-15 10:00:00.123 ERROR 123 --- [worker-1] c.p.checkout.PaymentService : Database connection pool exhausted after 5000ms",
    "2026-11-15 10:01:00.456 WARN  456 --- [worker-2] c.p.auth.TokenValidator : JWT token expiry approaching in 300 seconds",
    "2026-11-15 10:02:00.789 ERROR 789 --- [worker-3] c.p.inventory.StockService : NullPointerException at line 142: item lookup returned null",
    "2026-11-15 10:03:00.001 INFO  101 --- [main] c.p.order.OrderService : Order #98765 created successfully for user gokusengar",
    "2026-11-15 10:04:00.321 FATAL 202 --- [worker-5] c.p.payment.GatewayClient : Critical unhandled exception - stripe API timeout after 30000ms",
]

def run_ingestion_benchmark(url, label, headers):
    latencies = []
    errors = 0
    for i in range(N):
        log_msg = sample_logs[i % len(sample_logs)]
        payload = {
            "rawLog": log_msg,
            "source": "SPRING_BOOT",
            "environment": "PRODUCTION",
            "applicationName": f"benchmark-service-{i % 3}",
            "hostName": f"bench-host-{i % 5 + 1}",
        }
        try:
            start = time.perf_counter()
            r = requests.post(url, json=payload, headers=headers, timeout=10)
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code in (200, 201):
                latencies.append(elapsed_ms)
            else:
                errors += 1
                print(f"  [WARN] Request {i+1} failed: {r.status_code} - {r.text[:100]}")
        except Exception as e:
            errors += 1
            print(f"  [ERROR] Request {i+1} exception: {e}")

    if not latencies:
        print(f"[FAIL] No successful requests for {label}")
        return None

    s = sorted(latencies)
    result = {
        "benchmark": "01_ingestion_latency",
        "label": label,
        "url": url,
        "n_requests": N,
        "n_success": len(latencies),
        "n_errors": errors,
        "p50_ms": round(statistics.median(latencies), 2),
        "p75_ms": round(s[int(0.75 * len(s))], 2),
        "p95_ms": round(s[int(0.95 * len(s))], 2),
        "p99_ms": round(s[int(0.99 * len(s))], 2),
        "mean_ms": round(statistics.mean(latencies), 2),
        "stdev_ms": round(statistics.stdev(latencies) if len(latencies) > 1 else 0, 2),
        "min_ms": round(min(latencies), 2),
        "max_ms": round(max(latencies), 2),
        "throughput_rps": round(len(latencies) / (sum(latencies) / 1000), 2),
    }
    print(f"\n[{label}] Results:")
    print(f"  Successful: {result['n_success']}/{N}  |  Errors: {errors}")
    print(f"  p50: {result['p50_ms']} ms  |  p95: {result['p95_ms']} ms  |  p99: {result['p99_ms']} ms")
    print(f"  Mean: {result['mean_ms']} ms  |  Max: {result['max_ms']} ms  |  Throughput: {result['throughput_rps']} req/s")
    return result

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 01: Log Ingestion Latency")
    print("=" * 60)

    # Via API Gateway (with auth overhead)
    gw_result = run_ingestion_benchmark(
        GATEWAY_URL, "via_api_gateway",
        {"X-API-Key": API_KEY, "Content-Type": "application/json"}
    )

    # Via Log Layer directly (pure service latency)
    ll_result = run_ingestion_benchmark(
        LOG_LAYER_URL, "via_log_layer_direct",
        {"X-API-Key": API_KEY, "Content-Type": "application/json"}
    )

    final = {"gateway": gw_result, "log_layer_direct": ll_result}
    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/01_ingestion_latency.json", "w") as f:
        json.dump(final, f, indent=2)
    print("\n[OK] Results saved to benchmarks/results/01_ingestion_latency.json")

