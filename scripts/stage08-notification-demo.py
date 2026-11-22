"""Exercise the managed V8 fixture with real worker JVMs and a loopback provider."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from importlib.util import module_from_spec, spec_from_file_location
import json
import logging
import os
from pathlib import Path
import re
import subprocess
import sys
from threading import Event, Thread
from time import perf_counter, sleep
from uuid import UUID, uuid4
from xml.etree import ElementTree

spec = spec_from_file_location("stage06", Path(__file__).with_name("stage06-incident-demo.py"))
stage = module_from_spec(spec)
spec.loader.exec_module(stage)
demo, kafka = stage.demo, stage.kafka
PG = Path("C:/Program Files/PostgreSQL/16/bin")


def psql():
    return [str(PG / "psql.exe"), "-X", "-w", "-q", "-t", "-A", "-h", "127.0.0.1",
            "-p", "15438", "-U", "postgres", "-d", "log_layer_db", "-v", "ON_ERROR_STOP=1"]


def database_env(label="stage08_probe"):
    return {**os.environ, "PGPASSWORD": os.environ["STAGE08_ADMIN_PASSWORD"],
            "PGCONNECT_TIMEOUT": "3", "PGAPPNAME": label, "PGOPTIONS": "-c default_transaction_read_only=on"}


def query(sql):
    result = subprocess.run([*psql(), "-c", sql], env=database_env(), capture_output=True, timeout=8,
                            text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    demo.check(result.returncode == 0, "Stage 08 fixture query failed")
    return result.stdout.strip()


@contextmanager
def lock_row(table, scope, selector):
    label = "stage08_barrier_" + uuid4().hex
    cmd = subprocess.Popen(psql(), stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                           env=database_env(label), text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        cmd.stdin.write(f"begin read write; select 1 from public.{table} "
                        f"where organization_id='{scope}' and id='{selector}' for update;\n")
        cmd.stdin.flush()
        kafka.wait_for(lambda: query(f"select count(*) from pg_stat_activity where application_name='{label}' "
                                    "and state='idle in transaction'") == "1", "No fixture row lock", 12)
        yield
    finally:
        cmd.kill()
        cmd.communicate(timeout=5)


class Receiver:
    def __init__(self):
        self.reference = None
        self.records = []
        self.observed = Event()
        self.release = Event()
        self.acknowledged = Event()

    @contextmanager
    def running(self):
        target = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                size = int(self.headers.get("Content-Length", "0"))
                if self.path != "/notification" or not 0 < size < 12_000:
                    self.send_error(400)
                    return
                payload = json.loads(self.rfile.read(size))
                reference = UUID(payload["text"].split(" · ")[-1])
                target.records.append((reference, perf_counter()))
                if reference == target.reference:
                    target.observed.set()
                    target.release.wait(35)
                try:
                    self.send_response(200)
                    self.send_header("Content-Type", "text/plain")
                    self.send_header("Content-Length", "2")
                    self.end_headers()
                    self.wfile.write(b"ok")
                    if reference == target.reference:
                        target.acknowledged.set()
                except OSError:
                    pass

            def log_message(self, *_):
                pass

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = Thread(target=server.serve_forever, daemon=True)
            worker.start()
            self.url = f"http://127.0.0.1:{server.server_port}/notification"
            try:
                yield self
            finally:
                self.release.set()
                server.shutdown()
                worker.join(timeout=3)

    def count(self, reference):
        return sum(item == reference for item, _ in self.records)

    def hold(self, reference):
        self.reference = UUID(reference)
        self.observed.clear()
        self.release.clear()
        self.acknowledged.clear()


@contextmanager
def worker(archive, receiver, *, diagnose, deliver, llm_url="http://127.0.0.1:8084", diagnosis_attempts=4,
           diagnosis_delay="2s"):
    reports = sorted(Path("log-layer/target/surefire-reports").glob("TEST-*NotificationOutboxIntegrationTest-stage08*.xml"),
                     key=lambda path: path.stat().st_mtime, reverse=True)
    demo.check(reports, "Run the Stage 08 Java fixture tests before live verification")
    properties = ElementTree.parse(reports[0]).getroot().find("properties")
    cp = next(value.attrib["value"] for value in properties if value.attrib["name"] == "java.class.path")
    cp = ";".join(part.replace("\\", "/") for part in cp.split(";") if Path(part).name != "test-classes")
    args = archive / ("notification-worker-" + uuid4().hex + ".args")
    with args.open("x", encoding="utf-8") as output:
        output.write(f'-cp\n"{cp}"\ncom.project.log_layer.LogLayerApplication\n')
    env = {**os.environ, "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "0",
           "SPRING_DATASOURCE_URL": "jdbc:postgresql://127.0.0.1:15438/log_layer_db",
           "LOG_LAYER_DB_USERNAME": os.environ["STAGE08_RUNTIME_DB_USER"],
           "LOG_LAYER_DB_PASSWORD": os.environ["STAGE08_RUNTIME_DB_PASSWORD"],
           "INTERNAL_API_KEY": os.environ["STAGE08_INTERNAL_KEY"], "JWT_SECRET": os.environ["STAGE08_JWT"],
           "SLACK_WEBHOOK_ENCRYPTION_KEY": os.environ["STAGE08_WEBHOOK_KEY"],
           "NOTIFICATION_SLACK_ENABLED": "false", "NOTIFICATION_EMAIL_ENABLED": "false",
           "DEVINSIGHT_KAFKA_ENABLED": "false", "DIAGNOSIS_ENABLED": str(diagnose).lower(),
           "DIAGNOSIS_WORKERS": "1", "LLM_SERVICE_URL": llm_url,
           "DIAGNOSIS_ATTEMPTS": str(diagnosis_attempts),
           "DIAGNOSIS_INITIAL_DELAY": diagnosis_delay,
           "SPRING_PROFILES_ACTIVE": "stage08-fixture", "NOTIFICATION_DELIVERY_TRANSPORT": "FIXTURE",
           "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE": "health,metrics",
           "NOTIFICATION_DELIVERY_EXTERNAL": "false", "NOTIFICATION_DELIVERY_WORKER": str(deliver).lower(),
           "NOTIFICATION_DELIVERY_FIXTURE_URL": receiver.url, "NOTIFICATION_DELIVERY_WORKERS": "1",
           "NOTIFICATION_DELIVERY_LEASE": "12s", "NOTIFICATION_DELIVERY_HEARTBEAT": "1s",
           "NOTIFICATION_DELIVERY_HTTP_TIMEOUT": "10s"}
    with args.with_suffix(".log").open("xb") as diagnostic:
        process = subprocess.Popen(["C:/Program Files/Java/jdk-21/bin/java.exe", "@" + str(args)],
                                   cwd=Path.cwd() / "log-layer", env=env, stdout=diagnostic,
                                   stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            yield process,args.with_suffix(".log")
        finally:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=8)


def delivery(tenant, incident):
    data = stage.incidents(tenant, f"/{incident}/notifications")
    return data["content"][0] if data["content"] else None


def wait_status(tenant, incident, state, seconds=105):
    def found():
        try:
            item = delivery(tenant, incident)
            return item if item and item["status"] == state else None
        except (RuntimeError, OSError):
            return None

    return kafka.wait_for(found, "Notification did not reach " + state, seconds)


def configure(name="payment-service"):
    tenant = demo.owner()
    app = demo.application(tenant,name)
    key = demo.credential(tenant, app)[1]
    path = app[1].removesuffix("/credentials")
    demo.expect(204, demo.request("PUT", path + "/slack",
                {"url": "https://hooks.slack.com/services/FIXTURE/NOT_REAL/" + uuid4().hex},
                token=tenant[0]), "Configure synthetic webhook")
    return tenant, app, key, int(path.rsplit("/",1)[1])


def seed(tenant, app, key, trace=None):
    trace = trace or uuid4().hex
    entry = stage.event(datetime.now(timezone.utc), "ERROR", "Synthetic payment database connection refused")
    entry["traceparent"] = f"00-{trace}-baa79b0e776ff0e1-01"
    log = stage.sync(app,key,[entry])[0]
    return kafka.wait_for(lambda: stage.containing(tenant,log),"No incident for synthetic log",30)["id"]


def collect(tenant, app, key):
    sys.path.insert(0, str(Path("client-integrations/python").resolve()))
    from devinsight_logger import DevInsightHandler

    class RecordingHandler(DevInsightHandler):
        def __init__(self):
            self.batches = []
            super().__init__(api_key=key,application_name=app[0],environment="DEVELOPMENT",
                             endpoint="http://127.0.0.1:8080" + kafka.V2)

        def _send(self, body):
            self.batches.append(json.loads(body))
            return super()._send(body)

    trace = uuid4().hex
    old_mode = os.environ.get("DEVINSIGHT_DELIVERY_MODE")
    os.environ["DEVINSIGHT_DELIVERY_MODE"] = "ASYNC_KAFKA"
    try:
        collector = RecordingHandler()
        try:
            for number in range(1,13):
                record = logging.LogRecord("fixture.checkout",logging.ERROR,__file__,1,
                                           "Synthetic payment database connection refused",(),None)
                record.traceparent = f"00-{trace}-{number:016x}-01"
                collector.handle(record)
            collector.flush()
        finally:
            collector.close()
    finally:
        if old_mode is None:
            os.environ.pop("DEVINSIGHT_DELIVERY_MODE")
        else:
            os.environ["DEVINSIGHT_DELIVERY_MODE"] = old_mode
    demo.check(collector.stats()["brokerAccepted"] == 12,"Collector did not acknowledge twelve events")
    for batch in collector.batches:
        kafka.expect_v2(batch,key)
    organization = UUID(tenant[1])
    ids = {str(UUID(event["eventId"])) for batch in collector.batches for event in batch["events"]}
    demo.check(len(ids)==12,"Wrong number of distinct collector events")
    ids_sql = ",".join(f"'{identifier}'" for identifier in ids)
    rows = kafka.wait_for(lambda: (value if len(value := query(
        f"select id from public.logs where organization_id='{organization}' "
        f"and external_event_id in ({ids_sql})").splitlines())==12 else None),
        "Kafka fixture did not persist twelve distinct logs",50)
    incident = kafka.wait_for(lambda: stage.containing(tenant,rows[0]),"No incident for collector logs",30)
    kafka.wait_for(lambda: int(stage.incidents(tenant,f"/{incident['id']}")["eventCount"])==12,
                   "The twelve collector logs did not correlate",30)
    demo.check(stage.member_ids(tenant,incident)==set(rows),"Incident has unexpected members")
    return incident["id"], trace


def worker_port(log):
    def started():
        match = re.search(r"Tomcat started on port (\d+)",log.read_text(encoding="utf-8",errors="replace"))
        return int(match.group(1)) if match else None

    return kafka.wait_for(started,"Fixture notification API did not start",75)


def delivery_seconds(*workers):
    for log in workers:
        status, data = demo.request("GET", "/actuator/metrics/devinsight.slack.http?tag=outcome:DELIVERED",
                                    port=worker_port(log))
        if status == 200:
            measurements = {item["statistic"]:item["value"] for item in data["measurements"]}
            if measurements["COUNT"] == 1:
                return round(measurements["TOTAL_TIME"],6)
    raise RuntimeError("No authoritative delivery HTTP duration was observed")


def browser_delivery(tenant, app, state, *, unknown=False):
    from playwright.sync_api import expect, sync_playwright

    with sync_playwright() as tools:
        browser = tools.chromium.launch(channel="chrome",headless=True)
        page = browser.new_page()
        page.add_init_script(f"""
          localStorage.setItem("token",{json.dumps(tenant[0])});
          localStorage.setItem("devinsight.activeOrganization",{json.dumps(tenant[1])});
        """)
        page.goto("http://localhost:5173/incidents")
        page.get_by_role("button",name=app[0],exact=True).click(timeout=15000)
        panel = page.get_by_role("region",name="Slack notification")
        expect(panel.get_by_text(state,exact=False).first).to_be_visible(timeout=10000)
        panel.get_by_role("button",name="Attempt history").click()
        expect(panel.get_by_text("Attempt 1",exact=False)).to_be_visible()
        if unknown:
            with page.expect_event("dialog") as prompt:
                page.once("dialog",lambda dialog: dialog.dismiss())
                panel.get_by_role("button",name="Retry Slack notification").click()
            demo.check("already have accepted" in prompt.value.message,"Retry warning omitted possible duplication")
        browser.close()


def pg_restart(directory, action):
    args = [str(PG / "pg_ctl.exe"), "-D", str(directory), "-w", "-t", "20"]
    if action == "stop":
        args += ["-m", "fast", "stop"]
    else:
        args += ["-l", str(directory.parent / "stage08-pg-restart.log"), "-o", "-h 127.0.0.1 -p 15438", "start"]
    process = subprocess.run(args,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
                             timeout=25,creationflags=subprocess.CREATE_NO_WINDOW)
    demo.check(process.returncode == 0, "Isolated PostgreSQL fixture " + action + " failed")


def main(output):
    fixture = Path(os.environ["STAGE08_FIXTURE_PGDATA"]).resolve()
    demo.check(os.environ.get("STAGE08_MANAGED") == "15438" and not output.exists()
               and fixture.name == "pgdata-verified"
               and Path(query("select current_setting('data_directory')")).resolve() == fixture,
               "Run on the guarded disposable PostgreSQL fixture")
    with Receiver().running() as receiver:
        tenant, app, key, app_id = configure("collector-checkout")
        incident, trace = collect(tenant,app,key)
        with worker(output.parent,receiver,diagnose=True,deliver=False):
            pending = wait_status(tenant,incident,"PENDING",140)
        demo.check(stage.incidents(tenant,f"/{incident}/diagnosis")["status"] == "SUCCEEDED",
                   "Real Ollama result did not commit")
        demo.check(stage.incidents(tenant,f"/{incident}")["eventCount"] == 12,"Kafka replay changed membership")
        count = query(f"select count(*) from public.diagnosis_jobs where organization_id='{UUID(tenant[1])}'")
        demo.check(count=="1" and len(stage.incidents(tenant,f"/{incident}/notifications")["content"])==1,
                   "Collector replay created redundant diagnosis or delivery jobs")
        reference = UUID(pending["id"])
        demo.check(receiver.count(reference) == 0,"Delivery began while paused")
        print("Real Kafka intake and Ollama diagnosis committed one durable notification",flush=True)

        with lock_row("application_integrations",UUID(tenant[1]),app_id):
            with worker(output.parent,receiver,diagnose=False,deliver=True):
                wait_status(tenant,incident,"SENDING",75)
                demo.check(receiver.count(reference) == 0,"Dispatch bypassed destination locking")
        with worker(output.parent,receiver,diagnose=False,deliver=True) as (_,primary), worker(
                output.parent,receiver,diagnose=False,deliver=True) as (_,secondary):
            repaired = wait_status(tenant,incident,"DELIVERED",85)
            successful_http_seconds = delivery_seconds(primary,secondary)
        demo.check(repaired["id"] == str(reference) and repaired["attemptCount"] == 2
                   and receiver.count(reference) == 1,"Pre-dispatch recovery duplicated delivery")
        print("A killed worker recovered its pre-dispatch lease and sent once",flush=True)
        browser_delivery(tenant,app,"Delivered")

        other, app, key, _ = configure("interrupted-checkout")
        unsafe = seed(other,app,key,trace)
        demo.check(unsafe!=incident,"Reported trace joined tenants")
        with worker(output.parent,receiver,diagnose=True,deliver=False):
            entry = wait_status(other,unsafe,"PENDING",140)
        receiver.hold(entry["id"])
        with worker(output.parent,receiver,diagnose=False,deliver=True):
            demo.check(receiver.observed.wait(75),"No actual outbound HTTP request")
            started = perf_counter()
            fast = stage.event(datetime.now(timezone.utc),"INFO","Synthetic healthy request while webhook is held")
            kafka.expect_v2(stage.batch(app,[fast]),key)
            organization, event = UUID(other[1]),UUID(fast["eventId"])
            kafka.wait_for(lambda: query(
                f"select id from public.logs where organization_id='{organization}' and external_event_id='{event}'"),
                "Independent Kafka log did not persist during delayed delivery",5)
            demo.check(not receiver.acknowledged.is_set(),"Provider had already responded during ingestion check")
            ingestion_during_delivery_seconds = round(perf_counter()-started,3)
        receiver.release.set()
        with worker(output.parent,receiver,diagnose=False,deliver=True):
            unknown = wait_status(other,unsafe,"DELIVERY_UNKNOWN",75)
        demo.check(receiver.count(receiver.reference) == 1 and unknown["operatorReviewRequired"],
                   "An interrupted request should not be sent twice")
        print("Killing a worker during HTTP produced a durable unknown outcome",flush=True)
        browser_delivery(other,app,"Delivery unknown",unknown=True)

        blocked, app, key, _ = configure()
        lost = seed(blocked,app,key)
        with worker(output.parent,receiver,diagnose=True,deliver=False):
            entry = wait_status(blocked,lost,"PENDING",140)
        receiver.hold(entry["id"])
        with worker(output.parent,receiver,diagnose=False,deliver=True) as (sender,_):
            demo.check(receiver.observed.wait(75),"No outbound HTTP request before persistence fault")
            with lock_row("notification_outbox",UUID(blocked[1]),receiver.reference):
                receiver.release.set()
                demo.check(receiver.acknowledged.wait(4),"Local provider did not return its acknowledgement")
                kafka.wait_for(lambda: query("select count(*) from pg_stat_activity where wait_event_type='Lock' "
                    "and query like '%select max_attempts%' and query like '%for update%'")=="1",
                    "Delivery worker did not block before persisting success",5)
                wait_status(blocked,lost,"SENDING",4)
                sender.kill()
                sender.wait(timeout=8)
        with worker(output.parent,receiver,diagnose=False,deliver=True):
            wait_status(blocked,lost,"DELIVERY_UNKNOWN",80)
        demo.check(receiver.count(receiver.reference)==1,"Accepted request was sent again after termination")
        print("Sender terminated after acknowledgement while final commit was blocked",flush=True)

        retry_path = f"/api/v1/organizations/{blocked[1]}/notifications/{entry['id']}/retry"
        with worker(output.parent,receiver,diagnose=False,deliver=False) as (_,log):
            port = worker_port(log)
            def request_retry(confirmed):
                return demo.request("POST",retry_path,{"confirmPossibleDuplicate":confirmed},
                                    port=port,token=blocked[0])
            demo.expect(409,request_retry(False),"Explicit duplicate confirmation")
            with ThreadPoolExecutor(max_workers=2) as clients:
                results = list(clients.map(request_retry,[True,True]))
            demo.check(sorted(result[0] for result in results)==[202,409],"Concurrent retry was not fenced")
            retry = next(body for status,body in results if status==202)
            demo.check(retry["id"]==entry["id"] and retry["attemptCount"]==1,"Retry did not keep attempt history")
        with worker(output.parent,receiver,diagnose=False,deliver=True):
            wait_status(blocked,lost,"DELIVERED",80)
        demo.check(receiver.count(receiver.reference)==2,"Authorized uncertain retry did not demonstrate duplication")

        third, app, key, _ = configure()
        cutoff = seed(third,app,key)
        with worker(output.parent,receiver,diagnose=True,deliver=False):
            before = wait_status(third,cutoff,"PENDING",140)
        receiver.hold(before["id"])
        with worker(output.parent,receiver,diagnose=False,deliver=True):
            demo.check(receiver.observed.wait(75),"No outbound HTTP request before database fault")
            outage_started = perf_counter()
            pg_restart(fixture,"stop")
            try:
                receiver.release.set()
                sleep(14)
            finally:
                pg_restart(fixture,"start")
            uncertain = wait_status(third,cutoff,"DELIVERY_UNKNOWN",115)
            outage_recovery_seconds = round(perf_counter()-outage_started,3)
        demo.check(receiver.count(receiver.reference) == 1 and uncertain["id"] == before["id"],
                   "Database outage after provider acceptance should require review")
        print("PostgreSQL restarted after provider acknowledgement; no automatic resend",flush=True)

        forbidden = f"/api/v1/organizations/{third[1]}/notifications/{before['id']}"
        demo.expect(401, demo.request("GET",forbidden,key=key), "Ingestion key protection")
        demo.expect(404, demo.request("GET",f"/api/v1/organizations/{other[1]}/notifications/{before['id']}",
                                     token=other[0]), "Cross-tenant notification isolation")
        kafka.wait_for(lambda: kafka.kafka_status()["lag"] == 0,"Kafka lag remained",45)
        first_wait, repair_latency = map(float,query(f"""
            select extract(epoch from (min(a.started_at) - n.created_at)),
                extract(epoch from (max(a.finished_at) - min(a.started_at)))
            from public.notification_outbox n join public.notification_attempts a
            on a.organization_id=n.organization_id and a.notification_id=n.id
            where n.organization_id='{UUID(tenant[1])}' and n.id='{reference}'
            group by n.created_at""").split("|"))

    with output.open("x",encoding="utf-8") as receipt:
        json.dump(dict(real_kafka_and_ollama=True, collector_broker_acknowledgements=12, repeated_members=12,
                       logical_notifications=1, logical_diagnoses=1, delivered_http_requests=1,
                       worker_exit_after_result_commit=True, pre_dispatch_worker_exit_recovered=True,
                       simultaneous_worker_processes=2, in_flight_kill_unknown=True,
                       post_ack_process_exit_unknown=True, post_ack_database_outage_unknown=True,
                       secured_retry_statuses=[202,409], confirmed_retry_duplicate_requests=2,
                       separate_tenant_reports_for_same_trace=True, browser_delivery_and_unknown=True,
                       ingestion_key_http=401, foreign_http=404, kafka_lag=0,
                       successful_http_seconds=successful_http_seconds,
                       intake_during_delayed_http_seconds=ingestion_during_delivery_seconds,
                       initial_queue_wait_seconds=round(first_wait,3), recovery_after_first_claim_seconds=round(repair_latency,3),
                       postgres_outage_recovery_seconds=outage_recovery_seconds),receipt,indent=2)


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
