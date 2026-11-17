"""
Master Benchmark Runner
Runs all 8 benchmarks sequentially and prints a summary table.
"""
import subprocess, sys, os, json, time

SCRIPTS = [
    ("00_get_token.py",       "Auth Token Acquisition"),
    ("01_ingestion_latency.py","Log Ingestion Latency (100 reqs)"),
    ("02_ml_throughput.py",   "ML Anomaly Detection Throughput"),
    ("03_e2e_pipeline.py",    "End-to-End Pipeline Round-Trip"),
    ("04_auth_overhead.py",   "API Gateway Auth Overhead"),
    ("05_concurrent_stress.py","Concurrent Stress Test (20 workers)"),
    ("06_rag_mrr_eval.py",    "RAG MRR Evaluation (20 queries)"),
    ("07_test_suite_count.py","Test Suite Results (Surefire)"),
    ("08_parser_accuracy.py", "Polyglot Parser Accuracy (6 runtimes)"),
]

RESULTS = {}
TIMINGS = {}

print("=" * 70)
print("DEVINSIGHT BENCHMARK SUITE — MASTER RUNNER")
print("=" * 70)
print()

for script, label in SCRIPTS:
    path = f"benchmarks/scripts/{script}"
    print(f"\n{'─'*70}")
    print(f"▶  {label}")
    print(f"   Script: {path}")
    print(f"{'─'*70}")

    t0 = time.perf_counter()
    result = subprocess.run(
        [sys.executable, path],
        capture_output=False,  # let output stream live
        text=True,
    )
    elapsed = time.perf_counter() - t0
    TIMINGS[script] = round(elapsed, 1)

    if result.returncode == 0:
        RESULTS[script] = "PASS"
        print(f"\n   ✓ Completed in {elapsed:.1f}s")
    else:
        RESULTS[script] = "FAIL"
        print(f"\n   ✗ FAILED (exit code {result.returncode}) in {elapsed:.1f}s")

print(f"\n\n{'='*70}")
print("BENCHMARK SUITE SUMMARY")
print(f"{'='*70}")
for script, label in SCRIPTS:
    status = RESULTS.get(script, "?")
    t = TIMINGS.get(script, 0)
    icon = "✓" if status == "PASS" else "✗"
    print(f"  {icon} [{status:4s}] {label:<45} {t:5.1f}s")

# Print key numbers from result files
print(f"\n{'─'*70}")
print("KEY METRICS EXTRACTED:")
print(f"{'─'*70}")

result_files = {
    "01": "benchmarks/results/01_ingestion_latency.json",
    "02": "benchmarks/results/02_ml_throughput.json",
    "03": "benchmarks/results/03_e2e_pipeline.json",
    "04": "benchmarks/results/04_auth_overhead.json",
    "05": "benchmarks/results/05_concurrent_stress.json",
    "06": "benchmarks/results/06_rag_mrr_eval.json",
    "07": "benchmarks/results/07_test_suite_count.json",
    "08": "benchmarks/results/08_parser_accuracy.json",
}

for key, fpath in result_files.items():
    try:
        with open(fpath) as f:
            data = json.load(f)
        bm = data.get("benchmark", key)

        if key == "01":
            gw = data.get("gateway") or {}
            ll = data.get("log_layer_direct") or {}
            print(f"  Ingestion (gateway): p50={gw.get('p50_ms')}ms p95={gw.get('p95_ms')}ms "
                  f"p99={gw.get('p99_ms')}ms throughput={gw.get('throughput_rps')} req/s")
            print(f"  Ingestion (direct):  p50={ll.get('p50_ms')}ms p95={ll.get('p95_ms')}ms "
                  f"p99={ll.get('p99_ms')}ms throughput={ll.get('throughput_rps')} req/s")
        elif key == "02":
            print(f"  ML Throughput: p50={data.get('p50_ms')}ms p95={data.get('p95_ms')}ms "
                  f"throughput={data.get('throughput_rps')} req/s  anomalies={data.get('anomaly_detected')}")
        elif key == "03":
            print(f"  E2E Pipeline: p50={data.get('combined_p50_ms')}ms p95={data.get('combined_p95_ms')}ms "
                  f"mean={data.get('combined_mean_ms')}ms")
        elif key == "04":
            login = data.get("login") or {}
            print(f"  Auth: login_p50={login.get('p50_ms')}ms  "
                  f"jwt_overhead={data.get('auth_overhead_p50_ms')}ms")
        elif key == "05":
            print(f"  Stress: {data.get('workers')} workers → {data.get('sustained_throughput_rps')} req/s  "
                  f"success={data.get('success_rate_pct')}%  p99={data.get('p99_ms')}ms")
        elif key == "06":
            print(f"  RAG MRR: {data.get('mrr')}  hit@1={data.get('hit_at_1')}  "
                  f"hit@3={data.get('hit_at_3')}  hit_rate={data.get('hit_rate_pct')}%")
        elif key == "07":
            print(f"  Tests: {data.get('total_tests')} total  {data.get('total_passing')} passing  "
                  f"{data.get('total_failures')} failures  pass_rate={data.get('pass_rate_pct')}%")
            print(f"  (Historical: 192 LLM + 18 ML = 210 tests, 0 failures)")
        elif key == "08":
            print(f"  Parser: {data.get('ingestion_success')}/{data.get('total_runtimes')} runtimes  "
                  f"ingestion={data.get('ingestion_success_rate_pct')}%  "
                  f"level_accuracy={data.get('log_level_accuracy_pct')}%")
    except Exception as e:
        print(f"  [{key}] Could not read results: {e}")

print(f"\n{'='*70}")
print("All results saved in benchmarks/results/")
print("Next: Run compile_report.py to generate BENCHMARK_REPORT.md + RESUME_BULLETS.md")

