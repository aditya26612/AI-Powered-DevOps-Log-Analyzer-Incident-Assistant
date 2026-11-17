"""
Benchmark 06 (Fixed): RAG MRR Evaluation
Sends 20 real DevOps incident log payloads directly to LLM service (port 8084)
at POST /api/v1/llm/analyze, then scores keyword hit rate as a proxy for MRR.
"""
import requests, time, json, statistics, os

LLM_URL = "http://localhost:8084/api/v1/llm/analyze"
N_QUERIES = 20

QUERIES = [
    {"timestamp":"2026-11-15T03:00:00","level":"ERROR","service_name":"checkout-service",
     "message":"HikariPool-1 - Connection is not available, request timed out after 30000ms. Active: 50/50",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.42,"model_version":"1.0.0",
     "relevant":["connection pool","hikari","timeout","saturation","database"]},
    {"timestamp":"2026-11-15T03:05:00","level":"FATAL","service_name":"payment-gateway",
     "message":"java.lang.OutOfMemoryError: Java heap space at java.util.Arrays.copyOf – JVM heap exhausted, forcing process restart",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.78,"model_version":"1.0.0",
     "relevant":["heap","jvm","memory","outofmemory","gc"]},
    {"timestamp":"2026-11-15T03:10:00","level":"ERROR","service_name":"k8s-pod-scheduler",
     "message":"OOMKilled: container payment-pod exceeded memory limit 512Mi. Pod evicted from node gke-prod-cluster-1",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.65,"model_version":"1.0.0",
     "relevant":["oomkilled","kubernetes","memory","eviction","pod"]},
    {"timestamp":"2026-11-15T03:15:00","level":"ERROR","service_name":"cache-service",
     "message":"Error: connect ECONNREFUSED 127.0.0.1:6379 – Redis connection failed after 3 retries",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.51,"model_version":"1.0.0",
     "relevant":["redis","cache","connection","refused","retry"]},
    {"timestamp":"2026-11-15T03:20:00","level":"ERROR","service_name":"spring-app",
     "message":"Application context failed to load: BeanCreationException for bean 'dataSource' – could not autowire DataSource",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.38,"model_version":"1.0.0",
     "relevant":["spring","bean","startup","context","datasource"]},
    {"timestamp":"2026-11-15T03:25:00","level":"FATAL","service_name":"docker-runtime",
     "message":"container payment-svc CrashLoopBackOff: exit code 137. Restart policy: always. Restart count: 7",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.71,"model_version":"1.0.0",
     "relevant":["docker","crashloop","exit","restart","container"]},
    {"timestamp":"2026-11-15T03:30:00","level":"ERROR","service_name":"nginx-proxy",
     "message":"502 Bad Gateway: upstream connect error or disconnect/reset before headers. Upstream: http://app-service:8080",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.44,"model_version":"1.0.0",
     "relevant":["nginx","502","upstream","gateway","proxy"]},
    {"timestamp":"2026-11-15T03:35:00","level":"ERROR","service_name":"async-worker",
     "message":"Traceback (most recent call last): asyncio.exceptions.TimeoutError: coroutine exceeded 30s timeout in process_payment()",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.55,"model_version":"1.0.0",
     "relevant":["traceback","python","asyncio","timeout","coroutine"]},
    {"timestamp":"2026-11-15T03:40:00","level":"ERROR","service_name":"db-service",
     "message":"Deadlock detected: Transaction 7291 blocked by 7290 (InnoDB). Rolled back transaction on table orders.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.48,"model_version":"1.0.0",
     "relevant":["deadlock","transaction","innodb","rollback","lock"]},
    {"timestamp":"2026-11-15T03:45:00","level":"ERROR","service_name":"auth-service",
     "message":"JWT token expired at 2026-11-15T03:00:00Z. User gokusengar authentication failed. HTTP 401 Unauthorized.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.33,"model_version":"1.0.0",
     "relevant":["jwt","token","expired","auth","401"]},
    {"timestamp":"2026-11-15T04:00:00","level":"ERROR","service_name":"kafka-consumer",
     "message":"Consumer group checkout-group lag: 125000 messages on partition 3, topic checkout-events. Offset stuck at 892341.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.61,"model_version":"1.0.0",
     "relevant":["kafka","consumer","lag","partition","offset"]},
    {"timestamp":"2026-11-15T04:05:00","level":"ERROR","service_name":"container-runtime",
     "message":"CPU throttling detected: container payment-svc throttled for 85% of scheduling period. cgroup cpu.cfs_throttled_periods_total exceeded.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.46,"model_version":"1.0.0",
     "relevant":["cpu","throttle","cgroup","resource","limit"]},
    {"timestamp":"2026-11-15T04:10:00","level":"ERROR","service_name":"order-service",
     "message":"java.net.SocketTimeoutException: connect timed out after 5000ms to inventory-service:8083. Connection refused.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.52,"model_version":"1.0.0",
     "relevant":["socket","timeout","connection","refused","microservice"]},
    {"timestamp":"2026-11-15T04:15:00","level":"FATAL","service_name":"storage-service",
     "message":"No space left on device: /var/log/app.log. Filesystem /dev/sda1 100% full. Inode exhausted: df -i shows 100%",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.82,"model_version":"1.0.0",
     "relevant":["disk","filesystem","inode","storage","space"]},
    {"timestamp":"2026-11-15T04:20:00","level":"ERROR","service_name":"ssl-terminator",
     "message":"TLS handshake failed: certificate expired on 2026-11-10. PKIX path validation failed for api.example.com:443",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.57,"model_version":"1.0.0",
     "relevant":["ssl","tls","certificate","handshake","expired"]},
    {"timestamp":"2026-11-15T04:25:00","level":"ERROR","service_name":"thread-pool-service",
     "message":"Deadlock detected in thread pool: Thread-42 waiting for lock held by Thread-18. Synchronization failure in PaymentProcessor.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.63,"model_version":"1.0.0",
     "relevant":["deadlock","thread","synchronization","concurrent","lock"]},
    {"timestamp":"2026-11-15T04:30:00","level":"ERROR","service_name":"discovery-client",
     "message":"Consul service registration failed: health check endpoint /health returned 503 after 3 retries. Service deregistered.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.41,"model_version":"1.0.0",
     "relevant":["consul","service discovery","health check","registration","503"]},
    {"timestamp":"2026-11-15T04:35:00","level":"ERROR","service_name":"load-balancer",
     "message":"Circuit breaker OPEN: 503 Service Unavailable. payment-service health check failed 5 consecutive times. Failover to fallback.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.68,"model_version":"1.0.0",
     "relevant":["circuit breaker","503","health check","failover","fallback"]},
    {"timestamp":"2026-11-15T04:40:00","level":"ERROR","service_name":"memory-profiler",
     "message":"Memory leak detected: heap usage growing 50MB/hour. GC overhead limit exceeded. Heap dump triggered at 95% utilization.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.74,"model_version":"1.0.0",
     "relevant":["memory leak","heap","gc","dump","profiler"]},
    {"timestamp":"2026-11-15T04:45:00","level":"ERROR","service_name":"resilience-layer",
     "message":"Resilience4j CircuitBreaker 'payment' opened: 60% failure rate in 10s window. Bulkhead limit reached: 25/25 concurrent calls.",
     "prediction":-1,"prediction_label":"ANOMALY","decision_score":-0.69,"model_version":"1.0.0",
     "relevant":["circuit breaker","resilience4j","bulkhead","fallback","failure rate"]},
]

def calculate_rr(response_text, relevant_keywords):
    if not response_text:
        return 0.0
    text = str(response_text).lower()
    for i, kw in enumerate(relevant_keywords):
        if kw.lower() in text:
            return 1.0 / (i + 1)
    return 0.0

if __name__ == "__main__":
    print("=" * 60)
    print("BENCHMARK 06 (FIXED): RAG MRR Evaluation")
    print(f"Queries: {N_QUERIES}  |  LLM URL: {LLM_URL}")
    print("=" * 60)

    rr_scores = []
    response_times = []
    query_results = []
    endpoint_errors = 0

    for i, q in enumerate(QUERIES):
        payload = {
            "timestamp": q["timestamp"],
            "level": q["level"],
            "service_name": q["service_name"],
            "message": q["message"],
            "prediction": q.get("prediction", -1),
            "prediction_label": q.get("prediction_label", "ANOMALY"),
            "decision_score": q.get("decision_score", -0.5),
            "model_version": q.get("model_version", "1.0.0"),
        }
        relevant = q["relevant"]
        print(f"\nQuery {i+1:02d}: [{q['level']}] {q['service_name']} — {q['message'][:55]}...")
        try:
            start = time.perf_counter()
            r = requests.post(LLM_URL, json=payload,
                             headers={"Content-Type": "application/json"}, timeout=60)
            elapsed_ms = (time.perf_counter() - start) * 1000
            if r.status_code == 200:
                data = r.json()
                response_times.append(elapsed_ms)
                resp_text = json.dumps(data)
                rr = calculate_rr(resp_text, relevant)
                rr_scores.append(rr)
                matched = [kw for kw in relevant if kw.lower() in resp_text.lower()]
                summary = data.get("summary","")[:60] if isinstance(data, dict) else str(data)[:60]
                print(f"  RR={rr:.3f} | {elapsed_ms:.0f}ms | matched={matched[:2]} | summary: {summary}")
                query_results.append({
                    "query_idx": i+1, "service": q["service_name"],
                    "rr_score": rr, "elapsed_ms": round(elapsed_ms,1),
                    "keywords_matched": matched,
                    "llm_summary": summary,
                })
            else:
                endpoint_errors += 1
                rr_scores.append(0.0)
                print(f"  [ERROR] HTTP {r.status_code}: {r.text[:150]}")
                query_results.append({"query_idx": i+1, "rr_score": 0.0,
                                      "error": f"HTTP {r.status_code}"})
        except Exception as e:
            endpoint_errors += 1
            rr_scores.append(0.0)
            print(f"  [TIMEOUT/ERROR]: {e}")
            query_results.append({"query_idx": i+1, "rr_score": 0.0, "error": str(e)})

    mrr = round(statistics.mean(rr_scores), 4) if rr_scores else 0.0
    hit1 = sum(1 for s in rr_scores if s >= 1.0)
    hit3 = sum(1 for s in rr_scores if s >= 1/3)
    mean_rt = round(statistics.mean(response_times), 2) if response_times else None

    result = {
        "benchmark": "06_rag_mrr_eval_fixed",
        "url": LLM_URL,
        "n_queries": N_QUERIES,
        "corpus_size_docs": 21,
        "mrr": mrr,
        "hit_at_1": hit1,
        "hit_at_3": hit3,
        "hit_rate_pct": round(100*hit3/N_QUERIES, 1),
        "mean_response_ms": mean_rt,
        "successful_responses": len(response_times),
        "endpoint_errors": endpoint_errors,
        "individual_scores": query_results,
    }

    print(f"\n{'='*60}")
    print(f"MRR@K  : {mrr}")
    print(f"Hit@1  : {hit1}/{N_QUERIES}")
    print(f"Hit@3  : {hit3}/{N_QUERIES}  ({result['hit_rate_pct']}%)")
    print(f"Mean LLM response: {mean_rt} ms")
    print(f"Errors : {endpoint_errors}")

    os.makedirs("benchmarks/results", exist_ok=True)
    with open("benchmarks/results/06_rag_mrr_eval.json", "w") as f:
        json.dump(result, f, indent=2)
    print("[OK] Results saved to benchmarks/results/06_rag_mrr_eval.json")
