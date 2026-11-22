"""Exercise tenant incident semantics only on the managed Stage 06 fixture."""
from datetime import datetime, timedelta, timezone
from importlib.util import module_from_spec, spec_from_file_location
from itertools import combinations
import json
import os
from pathlib import Path
import statistics
import sys
from time import perf_counter, sleep
from uuid import uuid4

spec = spec_from_file_location("stage04", Path(__file__).with_name("stage04-kafka-demo.py"))
kafka = module_from_spec(spec)
spec.loader.exec_module(kafka)
demo = kafka.demo


def path(tenant, suffix=""):
    return f"/api/v1/organizations/{tenant[1]}/incidents{suffix}"


def incidents(tenant, suffix="", expected=200):
    return demo.expect(expected, demo.request("GET", path(tenant, suffix), token=tenant[0]), "Incident read")


def member_ids(tenant, incident):
    return {row["logId"] for row in incidents(tenant, f"/{incident['id']}/logs")["content"]}


def row(tenant, log_id):
    return demo.expect(200, demo.request("GET", f"/api/v1/logs/{log_id}", token=tenant[0],
        organization=tenant[1]), "Read fixture log")


def window():
    now = datetime.now(timezone.utc)
    base = int(now.timestamp()) // 300 * 300
    return datetime.fromtimestamp(base, timezone.utc)


def event(time, level, text, trace=None):
    output = {"eventId": str(uuid4()), "timestamp": time.isoformat(), "level": level,
              "message": text, "source": "PYTHON"}
    if trace:
        output["correlationId"] = trace
    return output


def batch(app, entries):
    return {"applicationName": app[0], "environment": "DEVELOPMENT", "events": entries}


def sync(app, key, entries):
    data = batch(app, entries)
    for _ in range(6):
        reply = demo.request("POST", kafka.V1, data, key=key)
        if reply[0] != 503:
            response = demo.expect(200, reply, "Synchronous fixture batch")
            return [item["logId"] for item in response["events"]]
        sleep(.08)
    raise RuntimeError("Synchronous incident retry exhausted")


def asynchronous(tenant, app, key, entry):
    payload = batch(app, [entry])
    kafka.expect_v2(payload, key)
    ids = kafka.wait_for(lambda: kafka.event_rows(tenant[1], entry["eventId"]), "Kafka event did not persist")
    return ids[0]


def containing(tenant, log_id):
    groups = incidents(tenant)["content"]
    return next((item for item in groups if str(log_id) in member_ids(tenant, item)), None)


def completed(tenant, incident_id):
    def status():
        info = incidents(tenant, f"/{incident_id}")
        return info if info["diagnosisStatus"] in ("COMPLETED", "FAILED") else None
    finished = kafka.wait_for(status, "Incident diagnosis did not finish", 125)
    demo.check(finished["diagnosisStatus"] == "COMPLETED"
               and bool(row(tenant, finished["diagnosisLogId"])["summary"]), "Ollama diagnosis was not persisted")
    return finished["diagnosisStatus"]


def action(tenant, incident, operation, version, expected):
    return demo.expect(expected, demo.request("POST", path(tenant, f"/{incident}/{operation}"),
        {"version": version}, token=tenant[0]), "Incident lifecycle")


def metrics(tenant, labels):
    groups = incidents(tenant)["content"]
    actual = {log_id: group["id"] for group in groups for log_id in member_ids(tenant, group)}
    tp = fp = fn = 0
    for a, b in combinations(labels, 2):
        expected = labels[a] is not None and labels[a] == labels[b]
        observed = a in actual and b in actual and actual[a] == actual[b]
        tp += expected and observed
        fp += not expected and observed
        fn += expected and not observed
    demo.check(tp > 0 and fp == fn == 0 and all((log_id in actual) == (label is not None)
        for log_id, label in labels.items()), "Synthetic fixture grouping differs from reviewed labels")
    return {"labeled_events": len(labels), "correct_pairs": tp, "false_merges": fp, "false_splits": fn,
            "precision": tp / (tp + fp), "recall": tp / (tp + fn), "incident_count": len(groups)}


def sample(tenant, app, key):
    creation, update = [], []
    started = perf_counter()
    clock = window() + timedelta(seconds=1)
    for i in range(12):
        trace = str(uuid4())
        for timings in (creation, update):
            before = perf_counter()
            sync(app, key, [event(clock, "ERROR", "Redis connection refused on port 6379", trace)])
            if i >= 2:
                timings.append((perf_counter() - before) * 1000)
    def summary(values):
        return {"n": len(values), "p50_ms": statistics.median(values), "p95_ms": max(values)}
    return {"create_sync": summary(creation), "update_sync": summary(update),
            "events": 24, "elapsed_seconds": perf_counter() - started}


def main(output):
    demo.check(os.getenv("DEVINSIGHT_STAGE06_MANAGED") == "55438", "Use the protected PowerShell workflow")
    if output.exists():
        raise FileExistsError("Supply an unused receipt path")
    first, second = demo.owner(), demo.owner()
    app_a, app_b, other = demo.application(first), demo.application(first, "api-gateway"), demo.application(second)
    key_a = demo.credential(first, app_a)[1]
    key_b = demo.credential(first, app_b)[1]
    other_key = demo.credential(second, other)[1]
    labels = {}
    base = window()
    def at(seconds):
        return base + timedelta(seconds=seconds)

    normal = "Redis connection refused on port 6379"
    repeat = [event(at(i), "ERROR", normal) for i in (24, 12, 18)]
    repeated_ids = sync(app_a, key_a, repeat)
    labels.update({item: "repeat" for item in repeated_ids})
    incident = containing(first, repeated_ids[0])
    demo.check(incident["eventCount"] == 3 and incident["diagnosisLogId"] == repeated_ids[0], "Repeated failures split")
    demo.check(all(row(first, item)["llmAnalysisStatus"] is None for item in repeated_ids[1:]),
               "A related log incorrectly received a diagnosis")
    demo.check(row(first, repeated_ids[0])["anomaly"] is False, "Expected a statistically normal operational seed")
    diagnosis = completed(first, incident["id"])
    retransmitted = sync(app_a, key_a, [repeat[0]])[0]
    demo.check(retransmitted == repeated_ids[0], "HTTP replay changed the V5 log identity")

    rest = sync(app_a, key_a, [event(at(13), "ERROR", "Independent payment database outage"),
        event(at(-900), "ERROR", normal), event(at(720), "ERROR", normal)])
    labels.update(dict(zip(rest, ("independent", None, None))))
    untraced_other = sync(app_b, key_b, [event(at(12), "ERROR", normal)])[0]
    labels[untraced_other] = "untraced_other"

    trace = str(uuid4())
    # Start with a definite seed and send an earlier related error through another transport.
    trace_log = sync(app_a, key_a, [event(at(29), "ERROR", normal, trace)])[0]
    kafka_event = event(at(9), "FATAL", "Dependency unavailable", trace)
    fatal_id = asynchronous(first, app_b, key_b, kafka_event)
    context = sync(app_a, key_a, [event(at(18), "INFO", "Application started successfully", trace)])[0]
    labels.update({trace_log: "trace", fatal_id: "trace", context: "trace"})
    trace_incident = containing(first, trace_log)
    demo.check(trace_incident["severity"] == "CRITICAL" and trace_incident["eventCount"] == 3,
               "Cross-service incident lost severity or members")
    demo.check(datetime.fromisoformat(trace_incident["firstEventAt"]) == at(9),
               "Out-of-order Kafka event did not update the first occurrence")
    demo.check({item["reason"] for item in incidents(first, f"/{trace_incident['id']}/logs")["content"]}
        == {"INITIAL_SEED", "SAME_SUPPLIED_TRACE", "TRACE_CONTEXT"}, "Unexpected trace membership evidence")
    # Match source event UUID, trace and timestamp while the authenticated tenant and application are different.
    other_trace_id = asynchronous(second, other, other_key, kafka_event)
    demo.check(other_trace_id != fatal_id and containing(second, other_trace_id)["id"] != trace_incident["id"],
               "Trace collided across tenants")
    incidents(second, f"/{trace_incident['id']}", 404)
    incidents(second, f"/{trace_incident['id']}/logs", 404)
    action(second, trace_incident["id"], "resolve", 0, 404)
    demo.expect(401, demo.request("GET", path(first), key=key_a), "Ingestion key cannot view incidents")

    oom = sync(app_a, key_a, [event(at(11), "INFO",
        "java.lang.OutOfMemoryError: Java heap space\n\tat java.base/java.util.Arrays.copyOf(Arrays.java:1)")])[0]
    demo.check(row(first, oom)["anomaly"] is True and containing(first, oom)["severity"] == "MEDIUM",
               "Real V2 anomaly did not qualify independently")
    labels[oom] = "statistical"
    evaluation = metrics(first, labels)

    target = trace_incident["id"]
    action(first, target, "acknowledge", 0, 200)
    action(first, target, "resolve", 0, 409)
    resolved = action(first, target, "resolve", 1, 200)
    demo.check(resolved["status"] == "RESOLVED" and resolved["resolvedAt"] is not None, "Resolution audit is absent")
    new_group_log = sync(app_a, key_a, [event(window() + timedelta(seconds=3), "ERROR", normal, trace)])[0]
    demo.check(containing(first, new_group_log)["id"] != target, "Resolution reopened the original incident")
    demo.check(incidents(first, "?status=RESOLVED&size=1")["content"][0]["id"] == target, "Incident filtering failed")

    kafka.run_ps("test-stage05.ps1", "-StopFixtureMl")
    outage_event = event(window() + timedelta(seconds=1), "ERROR", "Controlled fixture outage")
    outage_id = asynchronous(first, app_a, key_a, outage_event)
    demo.check(row(first, outage_id)["analysisStatus"] == "FAILED"
               and containing(first, outage_id)["severity"] == "HIGH", "ML outage discarded incident")
    kafka.run_ps("stop-all.ps1")
    kafka.run_ps("start-all.ps1", "-Kafka", "-KafkaFixture", "-DatabasePort", "55438")
    kafka.expect_v2(batch(app_b, [kafka_event]), key_b)
    kafka.wait_for(lambda: kafka.kafka_status()["lag"] == 0, "Recovery left broker lag", 40)
    demo.check(kafka.event_rows(first[1], kafka_event["eventId"]) == [fatal_id]
               and incidents(first, f"/{target}")["eventCount"] == 3, "Replay inflated incidents or logs")
    timing = sample(second, other, other_key)
    status = kafka.kafka_status()
    with output.open("x", encoding="utf-8") as receipt:
        json.dump(dict(grouping=evaluation, completed_diagnosis=diagnosis, eligible_repeat_events=3,
            repeat_diagnosis_owners=1, trace_cross_service_count=3, incident_replay_stable=True,
            tenant_isolation=True, failed_scoring_persisted=True, v2_anomaly_correlated=True,
            lifecycle_verified=True, lag=status["lag"], timings=timing), receipt, indent=2)
    print("Verified Stage 06 incident workflow")


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
