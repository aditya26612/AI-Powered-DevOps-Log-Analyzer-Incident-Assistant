"""Rehearse Stage 07 with actual worker JVMs and guarded local fault barriers."""
from contextlib import closing, contextmanager
from datetime import datetime, timedelta, timezone
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import subprocess
import sys
from threading import Event, Thread
from time import perf_counter
from uuid import UUID, uuid4
from xml.etree import ElementTree

spec = spec_from_file_location("stage06", Path(__file__).with_name("stage06-incident-demo.py"))
stage = module_from_spec(spec)
spec.loader.exec_module(stage)
demo, kafka = stage.demo, stage.kafka
PG = Path("C:/Program Files/PostgreSQL/16/bin")


def pg_command():
    return [str(PG / "psql.exe"), "-X", "-w", "-q", "-t", "-A", "-h", "127.0.0.1",
            "-p", "55438", "-U", "postgres", "-d", "log_layer_db"]


def pg_env(name="stage07_probe"):
    return {**os.environ, "PGPASSWORD": os.environ["DEVINSIGHT_STAGE04_FIXTURE_DB_PASSWORD"],
            "PGCONNECT_TIMEOUT": "3", "PGAPPNAME": name, "PGOPTIONS": "-c default_transaction_read_only=on"}


def pg(sql):
    result = subprocess.run([*pg_command(), "-c", sql], capture_output=True, env=pg_env(),
                            timeout=8, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:
        raise RuntimeError("Guarded fixture query failed")
    return result.stdout.strip()


@contextmanager
def barrier(sql):
    name = "stage07_barrier_" + uuid4().hex
    process = subprocess.Popen(pg_command(), stdin=subprocess.PIPE, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL, env=pg_env(name), text=True,
                               creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        # Temporary locks have no data writes. Use a normal read-write transaction to permit the requested lock.
        process.stdin.write("begin read write; " + sql + ";\n")
        process.stdin.flush()
        kafka.wait_for(lambda: pg("select count(*) from pg_stat_activity where application_name='"
                                 + name + "' and state='idle in transaction'") == "1",
                       "Fixture database barrier was not acquired", 12)
        yield
    finally:
        process.kill()
        process.communicate(timeout=5)


class Relay:
    def __init__(self, target_port=8084):
        self.mode = "forward"
        self.entered = Event()
        self.returned = Event()
        self.released = Event()
        self.request_held = Event()
        self.unblock_request = Event()
        self.marker = None
        self.calls = 0
        self.target_calls = 0
        self.target_durations_ms = []
        self.target_port = target_port
        self.context_verified = False

    @contextmanager
    def running(self):
        relay = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers["Content-Length"])
                if length > 128_000 or self.path != "/api/v1/llm/analyze":
                    self.send_error(400)
                    return
                data = self.rfile.read(length)
                targeted = relay.marker is not None and relay.marker.encode() in data
                if targeted:
                    relay.target_calls += 1
                if targeted and b"stage07 related evidence" in data:
                    relay.context_verified = b"stage07 foreign marker" not in data
                mode = relay.mode if targeted or relay.marker is None else "forward"
                relay.calls += 1
                try:
                    if mode == "hold_request":
                        relay.request_held.set()
                        relay.unblock_request.wait(30)
                        return
                    if mode == "outage":
                        code, response = 503, b"{}"
                    else:
                        started = perf_counter()
                        with closing(HTTPConnection("127.0.0.1", relay.target_port if targeted else 8084,
                                                    timeout=65)) as upstream:
                            upstream.request("POST", self.path, data, {
                                "Content-Type": "application/json",
                                "X-Internal-Api-Key": os.environ["STAGE07_INTERNAL_KEY"],
                            })
                            if targeted or relay.marker is None:
                                relay.entered.set()
                            reply = upstream.getresponse()
                            code, response = reply.status, reply.read(128_000)
                        if targeted:
                            relay.target_durations_ms.append(round((perf_counter() - started) * 1000, 2))
                    if mode == "hold_result":
                        relay.returned.set()
                        relay.released.wait(40)
                    self.send_response(code)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(response)))
                    self.end_headers()
                    self.wfile.write(response)
                except (OSError, TimeoutError):
                    pass

            def log_message(self, *_):
                pass

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            server.daemon_threads = True
            worker = Thread(target=server.serve_forever, daemon=True)
            worker.start()
            self.url = f"http://127.0.0.1:{server.server_port}"
            try:
                yield self
            finally:
                self.released.set()
                self.unblock_request.set()
                server.shutdown()
                worker.join(timeout=3)


def worker_args(directory):
    module = Path.cwd() / "log-layer"
    reports = sorted((module / "target/surefire-reports").glob("TEST-*DiagnosisQueueIntegrationTest-stage07*.xml"),
                     key=lambda path: path.stat().st_mtime, reverse=True)
    demo.check(reports, "Run the Java 21 diagnosis fixture tests before the crash workflow")
    props = ElementTree.parse(reports[0]).getroot().find("properties")
    cp = next(item.attrib["value"] for item in props if item.attrib["name"] == "java.class.path")
    cp = ";".join(segment.replace("\\", "/") for segment in cp.split(";")
                  if Path(segment).name != "test-classes")
    args = directory / ("diagnosis-worker-" + uuid4().hex + ".args")
    with args.open("x", encoding="utf-8") as target:
        target.write(f'-cp\n"{cp}"\ncom.project.log_layer.LogLayerApplication\n')
    return args


@contextmanager
def worker(directory, relay, *, delay="500ms", attempts="4"):
    env = {**os.environ, "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "0",
           "SPRING_DATASOURCE_URL": "jdbc:postgresql://127.0.0.1:55438/log_layer_db",
           "LOG_LAYER_DB_USERNAME": os.environ["STAGE07_RUNTIME_DB_USER"],
           "LOG_LAYER_DB_PASSWORD": os.environ["STAGE07_RUNTIME_DB_PASSWORD"],
           "INTERNAL_API_KEY": os.environ["STAGE07_INTERNAL_KEY"], "JWT_SECRET": os.environ["STAGE07_JWT"],
           "SLACK_WEBHOOK_ENCRYPTION_KEY": os.environ["STAGE07_WEBHOOK_KEY"],
           "DIAGNOSIS_ENABLED": "true", "DIAGNOSIS_WORKERS": "1", "DIAGNOSIS_LEASE": "6s",
           "DIAGNOSIS_HEARTBEAT": "1s", "DIAGNOSIS_INITIAL_DELAY": delay, "DIAGNOSIS_MAX_DELAY": "5s",
           "DIAGNOSIS_ATTEMPTS": attempts, "DIAGNOSIS_JITTER_PERCENT": "0",
           "DEVINSIGHT_KAFKA_ENABLED": "false", "LLM_SERVICE_URL": relay.url}
    args = worker_args(directory)
    java = "C:/Program Files/Java/jdk-21/bin/java.exe"
    with args.with_suffix(".log").open("xb") as diagnostic:
        process = subprocess.Popen([java, "@" + str(args)], cwd=Path.cwd() / "log-layer", env=env,
                                   stdout=diagnostic, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            yield process
        finally:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=8)


def view(tenant, incident):
    suffix = f"/{incident}/diagnosis"
    return stage.incidents(tenant, suffix)


def await_job(tenant, incident, state, seconds=85):
    def reached():
        result = view(tenant, incident)
        return result if result["status"] == state else None
    return kafka.wait_for(reached, f"Diagnosis did not reach {state}", seconds)


def seed(tenant, app, key, message, trace=None, *, at=None):
    entry = stage.event(at or datetime.now(timezone.utc), "ERROR", message)
    if trace:
        entry["traceparent"] = trace
        entry["traceOrigin"] = "INBOUND_REPORTED"
    identifier = stage.sync(app, key, [entry])[0]
    return stage.containing(tenant, identifier)["id"]


def main(output):
    demo.check(os.environ.get("DEVINSIGHT_STAGE07_MANAGED") == "55438" and not output.exists(),
               "Use the managed fixture and an unused evidence path")
    expected = Path(os.environ["STAGE07_FIXTURE_PGDATA"]).resolve()
    demo.check(expected.name == "pgdata-verified" and
               Path(pg("select current_setting('data_directory')")).resolve() == expected,
               "Unexpected fixture database identity")
    tenant, foreign = demo.owner(), demo.owner()
    app, peer, foreign_app = demo.application(tenant), demo.application(tenant, "api-gateway"), demo.application(foreign)
    key, peer_key, foreign_key = (demo.credential(owner, target)[1] for owner, target in
                                  ((tenant, app), (tenant, peer), (foreign, foreign_app)))
    trace = uuid4().hex
    now = datetime.now(timezone.utc)
    previous_boundary = datetime.fromtimestamp(int(now.timestamp()) // 300 * 300, timezone.utc)
    parent = f"00-{trace}-a1a1a1a1a1a1a1a1-01"
    marker = "stage07 primary evidence " + uuid4().hex
    primary = seed(tenant, app, key, marker, parent,
                   at=previous_boundary - timedelta(seconds=1))
    related = seed(tenant, peer, peer_key, "stage07 related evidence",
                   f"00-{trace}-b2b2b2b2b2b2b2b2-01", at=previous_boundary)
    demo.check(primary == related and stage.incidents(tenant, f"/{primary}")["eventCount"] == 2,
               "Cross-boundary rolling trace did not converge")
    foreign_incident = seed(foreign, foreign_app, foreign_key, "stage07 foreign marker", parent)
    demo.check(foreign_incident != primary, "W3C trace crossed tenant scope")
    stage.incidents(foreign, f"/{primary}/diagnosis", 404)
    queued = view(tenant, primary)
    demo.check(queued["status"] == "PENDING" and queued["attemptCount"] == 0,
               "The ingest fixture must begin with diagnosis disabled")
    # Kill the real importer after commit, then re-open the same database with diagnosis still disabled.
    kafka.run_ps("stop-all.ps1")
    os.environ["DIAGNOSIS_ENABLED"] = "false"
    kafka.run_ps("start-all.ps1", "-Kafka", "-KafkaFixture", "-DatabasePort", "55438")
    demo.check(view(tenant, primary)["id"] == queued["id"], "Job was lost after importer crash")
    print("Committed job survived importer restart", flush=True)

    directory = output.parent
    with Relay().running() as relay:
        relay.marker = marker
        relay.mode = "hold_request"
        with worker(directory, relay):
            demo.check(relay.request_held.wait(90) and view(tenant,primary)["status"] == "RUNNING",
                       "No durable claim at the pre-upstream request barrier")
            demo.check(not relay.entered.is_set(), "The upstream request barrier was bypassed")
        relay.unblock_request.set()
        relay.mode = "forward"
        # A fresh JVM must reclaim the original attempt and complete real RAG/Ollama.
        with worker(directory, relay), worker(directory, relay):
            completed = await_job(tenant, primary, "SUCCEEDED", 120)
            demo.check(completed["id"] == queued["id"] and completed["attemptCount"] == 2
                       and completed["attempts"][-1]["outcome"] == "LEASE_EXPIRED"
                       and completed["summary"] and relay.context_verified,
                       "Post-lease recovery or scoped RAG evidence was incorrect")
            # Allow the foreign synthetic control to settle before later checkpoints.
            await_job(foreign, foreign_incident, "SUCCEEDED", 100)
        print("Competing JVMs recovered the pre-upstream crash and completed scoped RAG", flush=True)

        in_flight = seed(tenant, app, key, "Controlled in-flight inference interruption")
        relay.marker = "Controlled in-flight inference interruption"
        relay.entered.clear()
        with worker(directory, relay):
            demo.check(relay.entered.wait(75), "Worker did not begin the real LLM HTTP request")
            demo.check(view(tenant, in_flight)["status"] == "RUNNING", "Expected a live lease")
        with worker(directory, relay):
            recovered = await_job(tenant, in_flight, "SUCCEEDED", 120)
            demo.check(recovered["attemptCount"] == 2, "In-flight diagnosis did not recover")
        print("Recovered the worker interrupted during the LLM request", flush=True)

        before_commit = seed(tenant, app, key, "Controlled interruption before result commit")
        relay.marker = "Controlled interruption before result commit"
        job_id = UUID(view(tenant, before_commit)["id"])
        relay.mode = "hold_result"
        relay.returned.clear()
        relay.released.clear()
        with worker(directory, relay) as process:
            demo.check(relay.returned.wait(100), "Real LLM Service did not return the result")
            with barrier(f"select id from public.diagnosis_jobs where id='{job_id}' for update"):
                relay.released.set()
                kafka.wait_for(lambda: pg("""
                    select count(*) from pg_stat_activity where wait_event_type='Lock'
                    and query ilike 'update public.diagnosis_jobs set status=%'
                    """) != "0", "Worker did not reach fenced result persistence", 12)
                process.kill()
                process.wait(timeout=8)
        relay.mode = "forward"
        with worker(directory, relay):
            preserved = await_job(tenant, before_commit, "SUCCEEDED", 120)
            demo.check(preserved["id"] == str(job_id) and preserved["attemptCount"] == 2,
                       "Uncommitted result crash did not recover")
        print("Recovered the worker interrupted at a locked result commit", flush=True)

        relay.mode = "outage"
        relay.marker = "Controlled temporary diagnosis dependency failure"
        retrying = seed(tenant, app, key, "Controlled temporary diagnosis dependency failure")
        started = perf_counter()
        with worker(directory, relay, delay="3s"):
            scheduled = await_job(tenant, retrying, "RETRY_WAIT")
            relay.mode = "forward"
            succeeded = await_job(tenant, retrying, "SUCCEEDED", 120)
            demo.check(succeeded["attemptCount"] == 2 and scheduled["failureCategory"] == "OVERLOADED",
                       "Retry state did not recover the original job")
        recovery_seconds = perf_counter() - started

        relay.mode = "outage"
        relay.marker = "Controlled diagnosis retry exhaustion"
        terminal = seed(tenant, app, key, "Controlled diagnosis retry exhaustion")
        with worker(directory, relay):
            exhausted = await_job(tenant, terminal, "FAILED_TERMINAL")
            demo.check(exhausted["attemptCount"] == exhausted["maxAttempts"], "Retry exhaustion was not bounded")
        caller = demo.expect(202, demo.request("POST", stage.path(tenant, f"/{terminal}/diagnosis/retry"),
            token=tenant[0]), "Request audited manual diagnosis retry")
        demo.check(caller["id"] == exhausted["id"] and caller["manualRetries"] == 1, "Manual retry changed identity")
        relay.mode = "forward"
        with worker(directory, relay):
            await_job(tenant, terminal, "SUCCEEDED", 120)

    kafka_event = stage.event(datetime.now(timezone.utc), "ERROR", "Stage 07 replay probe")
    original = stage.asynchronous(tenant, app, key, kafka_event)
    replay_incident = stage.containing(tenant, original)["id"]
    job = view(tenant, replay_incident)
    kafka.expect_v2(stage.batch(app, [kafka_event]), key)
    kafka.wait_for(lambda: kafka.kafka_status()["lag"] == 0, "Replay did not commit", 35)
    demo.check(kafka.event_rows(tenant[1], kafka_event["eventId"]) == [original]
               and view(tenant, replay_incident)["id"] == job["id"],
               "Kafka replay duplicated a diagnosis job or source log")
    with output.open("x", encoding="utf-8") as receipt:
        json.dump(dict(importer_crash=True, crash_before_request=True, restart_attempts=completed["attemptCount"],
                       worker_jvms_competed=True, scoped_incident_rag=relay.context_verified,
                       in_flight_llm_http_crash=True, result_commit_crash=True,
                       temporary_dependency_recovery=True, dependency_recovery_seconds=recovery_seconds,
                       retry_exhaustion=exhausted["attemptCount"], audited_manual_retry=True,
                       rolling_boundary=True, tenant_isolation=True, kafka_replay=True, lag=0), receipt, indent=2)
    print("Verified Stage 07 managed crash and recovery workflow")


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
