"""Drive the Kafka acceptance workflow on a managed disposable Windows stack."""
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import json
import os
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import traceback

ROOT = Path(__file__).resolve().parents[1]
spec = spec_from_file_location("stage03_demo", ROOT / "scripts" / "stage03-collector-demo.py")
demo = module_from_spec(spec)
spec.loader.exec_module(demo)
V2 = "/api/v2/logs/batch"
V1 = "/api/v1/logs/batch"

def run_ps(name, *arguments):
    # A regular output file keeps the launcher independent of the child broker's pipe lifetime.
    with tempfile.TemporaryFile() as output:
        process = subprocess.run(["powershell.exe", "-NoProfile", "-File", str(ROOT / "scripts" / name),
                                  *arguments], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=230)
        output.seek(0)
        text = output.read().decode(errors="replace")
    demo.check(process.returncode == 0, name + " failed in the disposable stack")
    return text

def kafka_status(name="test-kafka-local.ps1"):
    text = run_ps(name, "-Fixture")
    return json.loads(next(line for line in text.splitlines() if line.startswith('{"ready"')))

def wait_for(check, detail, seconds=45):
    until = time.monotonic() + seconds
    while time.monotonic() < until:
        answer = check()
        if answer:
            return answer
        time.sleep(.4)
    raise RuntimeError(detail)

def expect_v2(body, key):
    ack = demo.expect(202, demo.request("POST", V2, body, key=key), "Kafka batch")
    demo.check(ack["acknowledgement"] == "BROKER_ACCEPTED" and ack["processingState"] == "ASYNCHRONOUS"
               and [r["eventId"] for r in ack["events"]] == [e["eventId"] for e in body["events"]]
               and all(r["status"] == "BROKER_ACCEPTED" and "logId" not in r for r in ack["events"]),
               "Malformed v2 receipt")
    return ack

def run_collector(language, app, key, asynchronous):
    path = V2 if asynchronous else V1
    mode = "ASYNC_KAFKA" if asynchronous else "SYNC_PERSISTED"
    if language == "node" and os.getenv("DEVINSIGHT_STAGE04_NODE_SAMPLE"):
        environ = {k: v for k, v in os.environ.items()
                   if not k.startswith(("DEVINSIGHT_", "PG", "DB_", "LOG_LAYER_DB_", "SLACK_", "JWT_", "INTERNAL_"))}
        environ.update(DEVINSIGHT_APPLICATION_NAME=app, DEVINSIGHT_ENVIRONMENT="DEVELOPMENT", DEVINSIGHT_API_KEY=key,
                       DEVINSIGHT_ENDPOINT=f"http://127.0.0.1:8080{path}", DEVINSIGHT_DELIVERY_MODE=mode)
        child = subprocess.Popen(["node", os.environ["DEVINSIGHT_STAGE04_NODE_SAMPLE"]], cwd=ROOT, env=environ,
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    else:
        child = demo.process(language, app, key, endpoint=f"http://127.0.0.1:8080{path}", DELIVERY_MODE=mode)
    stats = demo.finish(child, language)
    demo.check(stats["acknowledged"] == 6 and stats["requests"] < 6
               and stats["brokerAccepted"] == (6 if asynchronous else 0)
               and stats["persisted"] == (0 if asynchronous else 6), "Wrong collector counters for " + language)
    return {k: stats[k] for k in ("captured", "requests", "acknowledged", "brokerAccepted", "persisted", "retries", "dropped")}

def lose_confirmation(body, key):
    observed = []
    class Relay(BaseHTTPRequestHandler):
        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            ack = expect_v2(payload, self.headers["X-API-Key"])
            observed.append(ack["receiptId"])
            self.close_connection = True
        def log_message(self, *_):
            pass
    with ThreadingHTTPServer(("127.0.0.1", 0), Relay) as server:
        worker = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": .05}, daemon=True)
        worker.start()
        try:
            try:
                demo.request("POST", V2, body, key=key, port=server.server_port)
            except (ConnectionError, OSError):
                pass
            else:
                raise RuntimeError("Relay should drop the already-confirmed acknowledgement")
        finally:
            server.shutdown()
            worker.join(timeout=2)
    demo.check(len(observed) == 1, "No broker-confirmed HTTP response was lost")
    expected = expect_v2(body, key)
    demo.check(expected["receiptId"] != observed[0], "Logical resubmission did not get a new broker receipt")

def event_rows(organization, event_id):
    # Identifiers originate in the disposable API fixture and are parsed before interpolation.
    from uuid import UUID
    org, eid = str(UUID(organization)), str(UUID(event_id))
    environment = {k:v for k,v in os.environ.items() if not k.startswith("PG")}
    environment.update(PGPASSWORD=os.environ["DEVINSIGHT_STAGE04_FIXTURE_DB_PASSWORD"],
                       PGOPTIONS="-c default_transaction_read_only=on", PGCONNECT_TIMEOUT="4")
    pieces = ["C:/Program Files/PostgreSQL/16/bin/psql.exe","-X","-w","-q","-t","-A","-h","127.0.0.1",
              "-p","55438","-U","postgres","-d","log_layer_db","-c",
              "select id from public.logs where organization_id='" + org + "' and external_event_id='" + eid + "'"]
    finished = subprocess.run(pieces, env=environment, capture_output=True, text=True, timeout=10)
    demo.check(finished.returncode == 0, "Disposable identity lookup failed")
    return [str(int(line)) for line in finished.stdout.splitlines() if line.strip()]

def samples(app, key):
    sync_times, async_times = [], []
    events = []
    for i in range(14):
        record = demo.event(app)
        start = time.perf_counter()
        demo.expect(200,demo.request("POST", V1, record, key=key),"Synchronous batch")
        elapsed = (time.perf_counter() - start) * 1000
        if i >= 2: sync_times.append(elapsed)
        queued = demo.event(app)
        start = time.perf_counter()
        expect_v2(queued, key)
        elapsed = (time.perf_counter() - start) * 1000
        events.append(queued["events"][0]["eventId"])
        if i >= 2: async_times.append(elapsed)
    return sync_times, async_times, events

def summarise(times):
    ordered = sorted(times)
    return {"samples":len(times), "median_ms":round(statistics.median(ordered),2),
            "max_ms":round(ordered[-1],2)}

def main():
    demo.check(os.getenv("DEVINSIGHT_STAGE04_MANAGED") == "55438", "Use the guarded PowerShell fixture launcher")
    first, second = demo.owner(), demo.owner()
    app_a, app_b = demo.application(first), demo.application(second)
    _, key_a = demo.credential(first,app_a)
    _, key_b = demo.credential(second,app_b)
    before = kafka_status()
    asynchronous = {
        language: run_collector(language, app_a[0] if language != "node" else app_b[0],
                                key_a if language != "node" else key_b, True)
        for language in ("java","python","node")
    }
    known = demo.event(app_a[0])
    known_id = known["events"][0]["eventId"]
    lose_confirmation(known, key_a)
    demo.check(not demo.logs(first, app_a[0]) and not demo.logs(second,app_b[0])
               and not event_rows(first[1], known_id), "Publisher-only stack persisted asynchronous records")
    after_publication = kafka_status()
    demo.check(after_publication["lag"] - before["lag"] == 5, "Expected five confirmed Kafka records with no worker")

    run_ps("stop-kafka-local.ps1","-Fixture")
    wait_for(lambda: demo.request("GET","/actuator/health",port=8082)[0] == 503,"Broker outage not reflected in health",20)
    during_outage = demo.event(app_a[0])
    outage_id = during_outage["events"][0]["eventId"]
    status, _ = demo.request("POST", V2, during_outage, key=key_a)
    demo.check(status == 503, "Unavailable Kafka returned a false success")
    sync_ack = demo.expect(200,demo.request("POST", V1, demo.event(app_a[0]),key=key_a),
                           "Synchronous batch during broker outage")
    demo.check(sync_ack["events"][0]["logId"].isdigit(), "Synchronous response lost its persisted ID")
    restarted = kafka_status("start-kafka-local.ps1")
    demo.check(restarted["clusterId"]==after_publication["clusterId"] and restarted["lag"]==after_publication["lag"],
               "Broker restart lost retained backlog or cluster identity")
    wait_for(lambda: demo.request("GET","/actuator/health",port=8082)[0] == 200, "Publisher did not recover",20)
    expect_v2(during_outage,key_a)
    sync_times, async_times, workload = samples(app_a[0],key_a)
    backlog = kafka_status()
    demo.check(backlog["lag"] - before["lag"] == 20, "Unexpected publisher-only backlog")

    run_ps("stop-all.ps1")
    resume = time.perf_counter()
    run_ps("start-all.ps1","-Kafka","-KafkaFixture","-DatabasePort","55438")
    wait_for(lambda: len(demo.logs(first,app_a[0])) == 43 and len(demo.logs(second,app_b[0])) == 6,
             "Kafka backlog did not persist all expected rows",40)
    duration = time.perf_counter() - resume
    ending = kafka_status()
    demo.check(ending["lag"] == 0, "Consumer group did not commit all drained offsets")
    ids = event_rows(first[1],known_id)
    demo.check(len(ids)==1 and len(event_rows(first[1],outage_id))==1 and
               all(len(event_rows(first[1], value))==1 for value in workload), "Replay persisted duplicate or missing rows")
    demo.expect(404,demo.request("GET","/api/v1/logs/"+ids[0],token=second[0],organization=second[1]),"Cross-tenant read")
    demo.expect(404,demo.request("GET","/api/v1/logs/147",token=first[0],organization=first[1]),"Unassigned historical read")
    row = next(r for r in demo.logs(first,app_a[0]) if r["source"]=="SPRING_BOOT" and r["level"]=="ERROR")
    def completed():
        status,value=demo.request("GET", "/api/v1/logs/"+str(row["id"]),token=first[0],organization=first[1])
        return value if status==200 and value.get("llmAnalysisStatus")=="COMPLETED" else None
    diagnosis=wait_for(completed,"Real Kafka ERROR diagnosis did not complete",120)
    normal = demo.expect(200, demo.request("GET","/api/v1/logs/"+ids[0],token=first[0],organization=first[1]),"Kafka normal inference")
    model = demo.expect(200,demo.request("GET","/api/v1/system/model",port=8000),"Active V2 model")
    demo.check(model["model_version"]=="2.0.0-candidate" and model["feature_count"]==40 and model["active_threshold"]==-0.02,
               "Unexpected model baseline")
    synchronous = {language:run_collector(language, app_a[0],key_a, False) for language in ("java","python","node")}
    pipeline_start=time.perf_counter()
    throughput_ids=[]
    for _ in range(12):
        record=demo.event(app_b[0])
        throughput_ids.append(record["events"][0]["eventId"])
        expect_v2(record,key_b)
    wait_for(lambda: len(demo.logs(second,app_b[0])) == 18,"Warm consumer measurement did not settle")
    processing_seconds = time.perf_counter()-pipeline_start
    demo.check(all(len(event_rows(second[1],value))==1 for value in throughput_ids),"Warm workload identities missing")

    print(json.dumps({"async_collectors":asynchronous,"sync_collectors":synchronous,
        "broker_outage_http":status, "cluster_and_backlog_preserved":True,
        "publisher_only_records":backlog["lag"]-before["lag"],"lag_after_drain":ending["lag"],
        "lag_after_warm_workload":kafka_status()["lag"],"startup_plus_drain_seconds":round(duration,2),
        "warm_events":12,"warm_publish_and_persist_seconds":round(processing_seconds,2),
        "warm_publish_and_persist_per_second":round(12/processing_seconds,2),
        "sync_ack_latency":summarise(sync_times),"kafka_ack_latency":summarise(async_times),
        "model":model["model_version"],"model_features":model["feature_count"],"model_threshold":model["active_threshold"],
        "sql_normal":{"score":normal["decisionScore"],"anomaly":normal["anomaly"],"analysis_status":normal["analysisStatus"]},
        "sql_error":{"score":diagnosis["decisionScore"],"anomaly":diagnosis["anomaly"],"diagnosis":diagnosis["llmAnalysisStatus"]},
        "loss_retried_once_in_sql":True, "two_tenants":True,"unassigned_hidden":True}, separators=(",",":")))

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, KeyError, TypeError, ValueError, subprocess.TimeoutExpired) as error:
        frame = traceback.extract_tb(error.__traceback__)[-1]
        sys.exit(str(error) if type(error) is RuntimeError else f"Fixture workflow failed at {frame.name}:{frame.lineno} ({type(error).__name__})")
