"""
Benchmark 05: Concurrent Stress Test
20 concurrent workers, each sending 25 requests = 500 total requests.
Measures system saturation point and sustained throughput.
"""
import requests, time, json, statistics, os
from concurrent.futures import ThreadPoolExecutor, as_completed

GATEWAY_URL = "http://localhost:8080/api/v1/logs"
API_KEY = "devinsight_internal_ml_key_secret_12345"
WORKERS = 20
REQUESTS_PER_WORKER = 25
TOTAL = WORKERS * REQUESTS_PER_WORKER

sample_logs = [
    "2026-11-15 10:00:00.123 ERROR 123 --- [worker-1] c.p.checkout.PaymentService : DB timeout",
    "2026-11-15 10:01:00.456 WARN  456 --- [worker-2] c.p.auth.TokenService : Token refresh failed",
    "2026-11-15 10:02:00.789 ERROR 789 --- [worker-3] c.p.inventory.StockService : Out of stock error",
    "2026-11-15 10:03:00.001 FATAL 101 --- [main] c.p.order.OrderService : Critical system failure",
    "2026-11-15 10:04:00.321 ERROR 202 --- [worker-5] c.p.payment.GatewayClient : Stripe API down",
]

def worker_task(worker_id, n):
    latencies = []
    errors = 0
    for i in range(n):
        payload = {
            "rawLog": sample_logs[(worker_id + i) % len(sample_logs)],
            "source": "SPRING_BOOT",
            "environment": "STAGING",
            "applicationName": f"stress-service-{worker_id % 4}",
            "hostName": f"stress-host-{worker_id}",
        }
        try:
            start = time.perf_counter()
            r = requests.post(
                GATEWAY_URL, json=payload,
                headers={"X-API-Key": API_KEY},
                timeout=15
            )
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code in (200, 201):
                latencies.append(elapsed_ms)
            else:
                errors += 1
        except Exception as e:
            errors += 1
    return {"worker_id": worker_id, "latencies": latencies, "errors": errors}

if __name__ == "__main__":
    print("=" * 60)
    print(f"BENCHMARK 05: Concurrent Stress Test")
    print(f"Workers: {WORKERS}  |  Requests/worker: {REQUESTS_PER_WORKER}  |  Total: {TOTAL}")
    print("=" * 60)

    all_latencies = []
    total_errors = 0
    worker_results = []

    wall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=WORKERS) as executor:
        futures = [executor.submit(worker_task, wid, REQUESTS_PER_WORKER) for wid in range(WORKERS)]
        for future in as_completed(futures):
            res = future.result()
            all_latencies.extend(res["latencies"])
            total_errors += res["errors"]
            worker_results.append(res)
            print(f"  Worker {res['worker_id']:02d}: {len(res['latencies'])} ok, {res['errors']} errors, "
                  f"p50={round(statistics.median(res['latencies']), 1) if res['latencies'] else 'N/A'} ms")
    wall_elapsed_s = time.perf_counter() - wall_start

    if all_latencies:
        s = sorted(all_latencies)
        sustained_rps = round(len(all_latencies) / wall_elapsed_s, 2)
        result = {
            "benchmark": "05_concurrent_stress",
            "workers": WORKERS,
            "requests_per_worker": REQUESTS_PER_WORKER,
            "total_requests": TOTAL,
            "n_success": len(all_latencies),
            "n_errors": total_errors,
            "success_rate_pct": round(100 * len(all_latencies) / TOTAL, 1),
            "wall_time_s": round(wall_elapsed_s, 2),
            "sustained_throughput_rps": sustained_rps,
            "p50_ms": round(statistics.median(all_latencies), 2),
            "p75_ms": round(s[int(0.75 * len(s))], 2),
            "p95_ms": round(s[int(0.95 * len(s))], 2),
            "p99_ms": round(s[int(0.99 * len(s))], 2),
            "mean_ms": round(statistics.mean(all_latencies), 2),
            "stdev_ms": round(statistics.stdev(all_latencies), 2),
            "min_ms": round(min(all_latencies), 2),
            "max_ms": round(max(all_latencies), 2),
        }
        print(f"\nAGGREGATE RESULTS:")
        print(f"  Total success: {len(all_latencies)}/{TOTAL}  ({result['success_rate_pct']}%)")
        print(f"  Wall time: {wall_elapsed_s:.1f}s  |  Sustained throughput: {sustained_rps} req/s")
        print(f"  p50: {result['p50_ms']} ms  |  p95: {result['p95_ms']} ms  |  p99: {result['p99_ms']} ms")
        print(f"  Stdev: {result['stdev_ms']} ms  |  Max: {result['max_ms']} ms")
    else:
        result = {"benchmark": "05_concurrent_stress", "error": "No successful requests"}

    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/05_concurrent_stress.json", "w") as f:
        json.dump(result, f, indent=2)
    print("[OK] Results saved to benchmarks/results/05_concurrent_stress.json")

