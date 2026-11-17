"""
Benchmark 02: ML Anomaly Detection Throughput
Sends 50 real log payloads directly to the ML pipeline and measures req/s and latency.
API schema: { timestamp, level, service_name, message }
"""
import requests, time, json, statistics, os

ML_URL = "http://localhost:8000/api/v1/predict"
N = 50

# Real log payloads matching the actual PredictionRequest schema
ANOMALOUS_LOGS = [
    {"timestamp": "2026-11-15T03:12:45", "level": "ERROR", "service_name": "checkout-service",
     "message": "Database connection pool exhausted after 5000ms. HikariPool-1 - Connection is not available, request timed out."},
    {"timestamp": "2026-11-15T03:15:00", "level": "FATAL", "service_name": "payment-gateway",
     "message": "java.lang.OutOfMemoryError: Java heap space at java.util.Arrays.copyOf – JVM heap exhausted"},
    {"timestamp": "2026-11-15T04:30:12", "level": "ERROR", "service_name": "user-service",
     "message": "Traceback (most recent call last): PoolTimeoutError: connection pool exhausted after 5000ms"},
]
NORMAL_LOGS = [
    {"timestamp": "2026-11-15T10:00:00", "level": "INFO", "service_name": "order-service",
     "message": "Order #98765 created successfully for user gokusengar"},
    {"timestamp": "2026-11-15T10:05:00", "level": "INFO", "service_name": "inventory-service",
     "message": "Stock check completed for SKU-4521: 142 units available"},
]

def make_payload(i):
    if i % 3 == 0:
        return ANOMALOUS_LOGS[i % len(ANOMALOUS_LOGS)]
    else:
        return NORMAL_LOGS[i % len(NORMAL_LOGS)]

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 02: ML Anomaly Detection Throughput")
    print("=" * 60)

    latencies = []
    errors = 0
    anomaly_count = 0
    normal_count = 0

    for i in range(N):
        payload = make_payload(i)
        try:
            start = time.perf_counter()
            r = requests.post(ML_URL, json=payload, timeout=10)
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code == 200:
                latencies.append(elapsed_ms)
                data = r.json()
                if data.get("anomaly") or data.get("is_anomaly") or data.get("prediction") == -1:
                    anomaly_count += 1
                else:
                    normal_count += 1
            else:
                errors += 1
                print(f"  [WARN] Request {i+1} status {r.status_code}: {r.text[:200]}")
        except Exception as e:
            errors += 1
            print(f"  [ERROR] Request {i+1}: {e}")

    if not latencies:
        print("[FAIL] No successful ML predictions")
    else:
        s = sorted(latencies)
        total_time_s = sum(latencies) / 1000
        result = {
            "benchmark": "02_ml_throughput",
            "url": ML_URL,
            "n_requests": N,
            "n_success": len(latencies),
            "n_errors": errors,
            "anomaly_detected": anomaly_count,
            "normal_classified": normal_count,
            "p50_ms": round(statistics.median(latencies), 2),
            "p95_ms": round(s[int(0.95 * len(s))], 2),
            "p99_ms": round(s[int(0.99 * len(s))], 2),
            "mean_ms": round(statistics.mean(latencies), 2),
            "min_ms": round(min(latencies), 2),
            "max_ms": round(max(latencies), 2),
            "throughput_rps": round(len(latencies) / total_time_s, 2),
            "detections_per_second": round(anomaly_count / total_time_s, 2),
        }
        print(f"\nResults:")
        print(f"  Success: {result['n_success']}/{N}  |  Anomalies: {anomaly_count}  |  Normal: {normal_count}")
        print(f"  p50: {result['p50_ms']} ms  |  p95: {result['p95_ms']} ms  |  p99: {result['p99_ms']} ms")
        print(f"  Mean: {result['mean_ms']} ms  |  Throughput: {result['throughput_rps']} req/s")

        os.makedirs("benchmarks/results", exist_ok=True)
        with open("benchmarks/results/02_ml_throughput.json", "w") as f:
            json.dump(result, f, indent=2)
        print("[OK] Results saved to benchmarks/results/02_ml_throughput.json")

