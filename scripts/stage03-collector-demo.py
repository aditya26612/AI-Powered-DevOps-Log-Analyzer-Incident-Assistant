"""Exercise the three example collectors through a managed disposable DevInsight launch."""

from contextlib import closing, contextmanager
from datetime import datetime, timezone
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import os
import secrets
import subprocess
import sys
import threading
import time
import traceback
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
BASE = "/api/v1"
BATCH = BASE + "/logs/batch"
GATEWAY = 8080
LOG_LAYER = 8082
EXAMPLES = ROOT / "examples" / "collectors"


def request(method, path, body=None, *, port=GATEWAY, token=None, key=None, organization=None):
    data = json.dumps(body, separators=(",", ":")).encode() if body is not None else None
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["X-API-Key"] = key
    if organization:
        headers["X-DevInsight-Organization"] = organization
    with closing(HTTPConnection("127.0.0.1", port, timeout=25)) as conn:
        conn.request(method, path, body=data, headers=headers)
        reply = conn.getresponse()
        payload = reply.read(256 * 1024 + 1)
        if len(payload) > 256 * 1024:
            raise RuntimeError("Oversized fixture response")
        if not payload:
            return reply.status, None
        try:
            return reply.status, json.loads(payload)
        except ValueError:
            return reply.status, None


def expect(code, reply, label):
    status, body = reply
    if status != code:
        raise RuntimeError(f"{label} returned HTTP {status}, expected {code}")
    return body


def owner():
    tag = uuid4().hex
    credentials = {"email": f"stage03-{tag}@example.invalid", "password": "Stage03!" + secrets.token_hex(16)}
    expect(200, request("POST", BASE + "/auth/register", credentials), "Register fixture owner")
    token = expect(200, request("POST", BASE + "/auth/login", credentials), "Fixture login")["token"]
    org = expect(201, request("POST", BASE + "/organizations", {"name": "Stage 03 fixture", "slug": "s03-" + tag},
                              token=token), "Create organization")["id"]
    return token, org


def application(tenant, name="payment-service"):
    token, org = tenant
    relative = BASE + "/organizations/" + org + "/applications"
    app = expect(201, request("POST", relative, {"applicationName": name, "environment": "DEVELOPMENT",
                                                "techStack": "CUSTOM"}, token=token), "Register app")["id"]
    return name, relative + "/" + app + "/credentials"


def credential(tenant, app):
    body = expect(201, request("POST", app[1], {"name": "Temporary collector demo"}, token=tenant[0]), "Issue key")
    return body["id"], body["key"]


def logs(tenant, app):
    token, org = tenant
    found = expect(200, request("POST", BASE + "/logs/search",
                                {"applicationName": app, "page": 0, "size": 100}, token=token, organization=org),
                   "Organization log search")
    return found["content"]


def event(app):
    return {"applicationName": app, "environment": "DEVELOPMENT", "events": [
        {"eventId": str(uuid4()), "timestamp": datetime.now(timezone.utc).isoformat(),
         "level": "INFO", "source": "PYTHON", "message": "Payment accepted in Stage 03 replay fixture"}]}


def process(language, app, key, *, count=6, delay=0, errors=True, endpoint=None, **limits):
    file = {
        "python": [sys.executable, str(EXAMPLES / "python" / "app.py")],
        "node": ["node", str(EXAMPLES / "nodejs" / "app.js")],
        "java": ["java", "-jar", str(EXAMPLES / "spring-boot" / "target" / "spring-boot-collector-demo-0.3.0.jar")],
    }[language]
    protected_prefixes = ("DEVINSIGHT_", "PG", "DB_", "LOG_LAYER_DB_", "SLACK_", "JWT_", "INTERNAL_")
    environment = {k: v for k, v in os.environ.items() if not k.startswith(protected_prefixes)}
    environment.update(DEVINSIGHT_APPLICATION_NAME=app, DEVINSIGHT_ENVIRONMENT="DEVELOPMENT", DEVINSIGHT_API_KEY=key,
                       DEVINSIGHT_ENDPOINT=endpoint or f"http://127.0.0.1:8080{BATCH}",
                       DEMO_EVENTS=str(count), DEMO_DELAY_MS=str(delay), DEMO_ERRORS=str(errors).lower(),
                       PYTHONPATH=str(ROOT / "client-integrations" / "python"))
    environment.update({"DEVINSIGHT_" + k: str(v) for k, v in limits.items()})
    return subprocess.Popen(file, cwd=ROOT, env=environment, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")


def finish(child, language):
    try:
        output, _ = child.communicate(timeout=75)
    except subprocess.TimeoutExpired:
        child.kill()
        child.communicate()
        raise RuntimeError(f"{language} sample exceeded its test deadline") from None
    safe_lines = [line.removeprefix("DEVINSIGHT_STATS ") for line in output.splitlines()
                  if line.startswith("DEVINSIGHT_STATS ")]
    if child.returncode or len(safe_lines) != 1:
        raise RuntimeError(f"{language} sample did not produce one statistics record")
    return json.loads(safe_lines[0])


@contextmanager
def receiver(*, lose_first_ack=False, delay_start=0):
    seen = []
    class Relay(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != BATCH:
                self.send_error(404)
                return
            data = self.rfile.read(int(self.headers["Content-Length"]))
            parsed = json.loads(data)
            status, payload = request("POST", BATCH, parsed, key=self.headers["X-API-Key"])
            seen.append((status, payload))
            if len(seen) == 1 and status == 200 and lose_first_ack:
                self.close_connection = True
                return
            response = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response)))
            self.end_headers()
            self.wfile.write(response)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Relay, bind_and_activate=False)
    server.server_bind()
    timer = None
    if not delay_start:
        server.server_activate()
    else:
        def listen_after_outage():
            time.sleep(delay_start)
            server.server_activate()
        timer = threading.Thread(target=listen_after_outage)
        timer.start()
    worker = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True)
    worker.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}{BATCH}", seen
    finally:
        if timer:
            timer.join(timeout=2)
        server.shutdown()
        server.server_close()
        worker.join(timeout=2)


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def restart_fixture():
    for script, args in (("stop-all.ps1", []), ("start-all.ps1", ["-DatabasePort", "55437"])):
        response = subprocess.run(["powershell.exe", "-NoProfile", "-File", str(ROOT / "scripts" / script), *args],
                                  capture_output=True, cwd=ROOT, timeout=220)
        check(response.returncode == 0, "Managed disposable " + script + " failed")


def main():
    check(os.getenv("DEVINSIGHT_STAGE03_DEMO_MANAGED") == "55437",
          "Use test-stage03-collectors.ps1 to verify the managed disposable target first")
    token_a, org_a = first = owner()
    token_b, org_b = second = owner()
    app_a, app_b = application(first), application(second)
    _, key_a = credential(first, app_a)
    old_b, key_b = credential(second, app_b)
    report = {"collectors": {}}
    for lang, app, key in [("java", app_a, key_a), ("python", app_a, key_a), ("node", app_b, key_b)]:
        stats = finish(process(lang, app[0], key), lang)
        check(stats["acknowledged"] == 6 and stats["requests"] < 6, f"{lang} did not acknowledge six events in batches")
        report["collectors"][lang] = stats

    logs_a, logs_b = logs(first, app_a[0]), logs(second, app_b[0])
    check({log["source"] for log in logs_a} == {"SPRING_BOOT", "PYTHON"} and
          {log["source"] for log in logs_b} == {"NODEJS"}, "Unexpected source data in tenant results")
    check(len(logs_a) == 12 and len(logs_b) == 6, "Unexpected tenant row counts")
    for row in logs_a + logs_b:
        check(row["modelVersion"] == "2.0.0-candidate" and row["analysisStatus"] == "COMPLETED",
              "V2 scoring did not complete on a collector row")
    expect(404, request("GET", BASE + "/logs/" + str(logs_b[0]["id"]), token=token_a, organization=org_a), "Cross-tenant read")
    expect(404, request("GET", BASE + "/logs/147", token=token_a, organization=org_a), "Unassigned historical read")
    invalid = event(app_a[0])
    invalid["organizationId"] = org_b
    expect(400, request("POST", BATCH, invalid, key=key_a), "Forged ownership payload")
    expect(401, request("POST", BATCH, event(app_a[0]), port=LOG_LAYER, organization=org_a), "Direct header forgery")
    normal = next(row for row in logs_a if row["level"] == "INFO")
    error = next(row for row in logs_a if row["level"] == "ERROR" and row["source"] == "SPRING_BOOT")
    states = set()
    end = time.monotonic() + 120
    while time.monotonic() < end:
        current = expect(200, request("GET", BASE + "/logs/" + str(error["id"]),
                                      token=token_a, organization=org_a), "Fetch operational diagnosis")
        states.add(current["llmAnalysisStatus"])
        if current["llmAnalysisStatus"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(0.5)
    check("COMPLETED" in states and "FAILED" not in states, "Real external ERROR diagnosis did not complete")
    report["pipeline"] = {"rows_scored": len(logs_a) + len(logs_b), "v2_version": normal["modelVersion"],
                          "normal_anomaly": normal["anomaly"], "normal_score": normal["decisionScore"],
                          "error_anomaly": current["anomaly"], "error_score": current["decisionScore"],
                          "observed_diagnosis": sorted(states - {None}),
                          "stack_trace_preserved": "Synthetic database timeout" in current["message"]}
    check(report["pipeline"]["stack_trace_preserved"], "The external ERROR stack trace was not preserved")
    report["tenant_isolation"] = {"a": len(logs_a), "b": len(logs_b), "unassigned_hidden": True,
                                  "forged_body_and_direct_headers_rejected": True}

    standalone = application(first, "stage03-collector-probes")
    _, probe_key = credential(first, standalone)
    burst = finish(process("node", standalone[0], probe_key, count=40, errors=False, QUEUE_CAPACITY=16), "Node burst")
    check(burst["peakDepth"] <= 16 and burst["dropped"] > 0 and burst["requests"] < burst["acknowledged"]
          and burst["captured"] == burst["acknowledged"] + burst["dropped"], "Burst accounting failed")
    report["burst"] = burst

    with receiver(delay_start=0.95) as (url, replies):
        outage = finish(process("python", standalone[0], probe_key, count=30, delay=45, errors=False,
                                endpoint=url, BATCH_COUNT=5, QUEUE_CAPACITY=6, CONNECT_TIMEOUT_MS=100), "Python outage")
        check(outage["retries"] > 0 and outage["acknowledged"] > 0 and outage["peakDepth"] <= 6
              and outage["captured"] == outage["acknowledged"] + outage["rejected"] + outage["dropped"],
              "Outage accounting or recovery failed: " + json.dumps(outage, separators=(",", ":")))
        report["outage"] = outage

    with receiver(lose_first_ack=True) as (url, replies):
        lost_ack = finish(process("python", standalone[0], probe_key, count=1, errors=False, endpoint=url), "Python lost ACK")
        check(lost_ack["requests"] == 2 and lost_ack["duplicates"] == 1 and lost_ack["acknowledged"] == 1,
              "Lost acknowledgement was not resolved by a duplicate")
        a, b = [payload["events"][0] for _, payload in replies]
        check(a["status"] == "ACCEPTED" and b["status"] == "DUPLICATE"
              and a["logId"] == b["logId"] and a["eventId"] == b["eventId"], "Replay did not retain one row")
        report["lost_ack"] = lost_ack

    _, replacement = credential(second, app_b)
    hop = event(app_b[0])
    confirmed = expect(200, request("POST", BATCH, hop, key=replacement), "Verify replacement key")
    expect(204, request("DELETE", app_b[1] + "/" + old_b, token=token_b), "Revoke disposable key")
    expect(401, request("POST", BATCH, hop, key=key_b), "Gateway revoked key")
    expect(401, request("POST", BATCH, hop, key=key_b, port=LOG_LAYER), "Direct revoked key")
    renewed = finish(process("node", app_b[0], replacement, count=1, errors=False), "Node replacement")
    check(renewed["acknowledged"] == 1, "Collector could not use replacement credential")
    report["key_replacement"] = {"verified_before_revoking": True, "old_key_rejected_both_routes": True, "collector_recovered": True}

    if "--restart-stack" in sys.argv[1:]:
        restart_fixture()
        repeated = expect(200, request("POST", BATCH, hop, key=replacement), "Replay after restart")["events"][0]
        original = confirmed["events"][0]
        check(repeated["logId"] == original["logId"] and repeated["status"] == "DUPLICATE",
              "Deduplication failed after restart")
        resumed = finish(process("node", app_b[0], replacement, count=1, errors=False), "Node after restart")
        check(resumed["acknowledged"] == 1, "Collector did not reconnect after restart")
        expect(404, request("GET", BASE + "/logs/" + repeated["logId"], token=token_a, organization=org_a),
               "Cross-tenant read after restart")
        report["restart"] = {"event_still_deduplicates": True, "collector_reconnected": True, "isolation_preserved": True}
    print(json.dumps(report, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, TimeoutError, KeyError, ValueError, subprocess.TimeoutExpired) as error:
        location = traceback.extract_tb(error.__traceback__)[-1]
        message = str(error) if type(error) is RuntimeError else f"Fixture workflow failed at {location.name}:{location.lineno} ({type(error).__name__})"
        sys.exit(message)
