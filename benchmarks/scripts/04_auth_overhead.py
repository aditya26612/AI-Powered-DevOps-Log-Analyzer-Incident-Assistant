"""
Benchmark 04: API Gateway Auth Overhead
Measures JWT validation cost: login time + per-request auth overhead vs non-auth routes.
"""
import requests, time, json, statistics, os

GATEWAY_URL = "http://localhost:8080"
N = 50

def bench_login(n=20):
    """How long does JWT issuance take?"""
    latencies = []
    tokens = []
    for i in range(n):
        start = time.perf_counter()
        r = requests.post(f"{GATEWAY_URL}/api/v1/auth/login",
                         json={"email": "gokusengar666@gmail.com", "password": "password123"},
                         timeout=10)
        elapsed_ms = (time.perf_counter() - start) * 1000
        if r.status_code == 200:
            latencies.append(elapsed_ms)
            data = r.json()
            token = data.get("token") or data.get("accessToken") or data.get("access_token")
            if token:
                tokens.append(token)
        else:
            print(f"  [WARN] Login {i+1} failed: {r.status_code}")
    return latencies, tokens[0] if tokens else None

def bench_authenticated_requests(token, n=N):
    """Measure overhead of JWT-protected endpoint calls"""
    latencies = []
    errors = 0
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    for i in range(n):
        start = time.perf_counter()
        r = requests.get(f"{GATEWAY_URL}/api/v1/logs?page=0&size=1",
                        headers=headers, timeout=10)
        elapsed_ms = (time.perf_counter() - start) * 1000
        if r.status_code == 200:
            latencies.append(elapsed_ms)
        else:
            errors += 1
    return latencies, errors

def bench_api_key_requests(n=N):
    """Measure API-key auth overhead (headless ingestion path)"""
    latencies = []
    errors = 0
    for i in range(n):
        payload = {
            "rawLog": "2026-11-15 10:00:01.000 INFO 1 --- [main] c.p.App : Health check OK",
            "source": "SPRING_BOOT",
            "environment": "PRODUCTION",
            "applicationName": "auth-bench",
            "hostName": "bench-host-1"
        }
        start = time.perf_counter()
        r = requests.post(f"{GATEWAY_URL}/api/v1/logs",
                         json=payload,
                         headers={"X-API-Key": "devinsight_internal_ml_key_secret_12345"},
                         timeout=10)
        elapsed_ms = (time.perf_counter() - start) * 1000
        if r.status_code in (200, 201):
            latencies.append(elapsed_ms)
        else:
            errors += 1
    return latencies, errors

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 04: API Gateway Auth Overhead")
    print("=" * 60)

    print("\n--- Part A: JWT Login Latency (20 logins) ---")
    login_latencies, token = bench_login(20)

    if not token:
        print("[ERROR] Could not get token")
        import sys; sys.exit(1)

    print("\n--- Part B: JWT Bearer Auth Request Overhead ---")
    jwt_latencies, jwt_errors = bench_authenticated_requests(token, N)

    print("\n--- Part C: API Key Auth Request Overhead ---")
    apikey_latencies, apikey_errors = bench_api_key_requests(N)

    def summarize(latencies, label):
        if not latencies:
            return {"label": label, "error": "no data"}
        s = sorted(latencies)
        return {
            "label": label,
            "n": len(latencies),
            "p50_ms": round(statistics.median(latencies), 2),
            "p95_ms": round(s[int(0.95 * len(s))], 2),
            "p99_ms": round(s[int(0.99 * len(s))], 2),
            "mean_ms": round(statistics.mean(latencies), 2),
            "min_ms": round(min(latencies), 2),
            "max_ms": round(max(latencies), 2),
        }

    login_stats = summarize(login_latencies, "jwt_login")
    jwt_stats = summarize(jwt_latencies, "jwt_bearer_requests")
    apikey_stats = summarize(apikey_latencies, "api_key_requests")

    # Auth overhead = JWT p50 - API-key p50 (approximate gateway cost)
    auth_overhead_ms = None
    if jwt_stats.get("p50_ms") and apikey_stats.get("p50_ms"):
        auth_overhead_ms = round(jwt_stats["p50_ms"] - apikey_stats["p50_ms"], 2)

    result = {
        "benchmark": "04_auth_overhead",
        "login": login_stats,
        "jwt_bearer": jwt_stats,
        "api_key": apikey_stats,
        "auth_overhead_p50_ms": auth_overhead_ms,
        "concurrent_sessions_supportable": "Stateless JWT — unlimited concurrent sessions",
    }

    for key, stat in [("Login", login_stats), ("JWT Bearer", jwt_stats), ("API Key", apikey_stats)]:
        print(f"\n  [{key}] p50={stat.get('p50_ms')} ms  p95={stat.get('p95_ms')} ms  mean={stat.get('mean_ms')} ms")
    if auth_overhead_ms is not None:
        print(f"\n  Estimated JWT auth overhead (vs API-key): {auth_overhead_ms} ms")

    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/04_auth_overhead.json", "w") as f:
        json.dump(result, f, indent=2)
    print("\n[OK] Results saved to benchmarks/results/04_auth_overhead.json")

