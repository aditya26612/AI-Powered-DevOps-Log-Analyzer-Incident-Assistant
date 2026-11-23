"""Measure bounded local Kafka submissions, persisted events, and dashboard reads."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager, nullcontext
import ctypes
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import shutil
import statistics
import sys
from threading import Event
from time import monotonic, sleep
from uuid import UUID, uuid5

spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("stage08-notification-demo.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
demo, stage, kafka = fixture.demo, fixture.stage, fixture.kafka


class Memory(ctypes.Structure):
    _fields_ = [("size", ctypes.c_uint32), ("load", ctypes.c_uint32)] + [
        (field, ctypes.c_uint64) for field in ("total", "available", "page", "available_page", "virtual",
                                             "available_virtual", "extended")
    ]


def resource_gate(directory):
    memory = Memory()
    memory.size = ctypes.sizeof(memory)
    demo.check(ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory)), "Memory probe failed")
    free = round(memory.available / 1024 ** 2)
    demo.check(free >= 512 and shutil.disk_usage(directory).free >= 5 * 1024 ** 3, "Resource floor reached")
    return free


def timings(samples):
    ordered = sorted(samples)
    report = {"samples": len(samples)}
    if ordered:
        report["p50_ms"] = round(statistics.median(samples) * 1000, 2)
    if len(samples) >= 20:
        report["p95_ms"] = round(ordered[(95 * len(samples) + 99) // 100 - 1] * 1000, 2)
    return report


@contextmanager
def hold_log_writes():
    child = fixture.subprocess.Popen(fixture.psql(), stdin=fixture.subprocess.PIPE, stdout=fixture.subprocess.DEVNULL,
        stderr=fixture.subprocess.DEVNULL, env=fixture.database_env("stage10_lock"),
        text=True, creationflags=fixture.subprocess.CREATE_NO_WINDOW)
    try:
        child.stdin.write("begin read write; lock table public.logs in share mode;\n")
        child.stdin.flush()
        kafka.wait_for(lambda: fixture.query("select count(*) from pg_stat_activity where application_name="
            "'stage10_lock' and state='idle in transaction'") == "1", "Fixture write lock missing", 8)
        yield
    finally:
        child.kill()
        child.communicate(timeout=5)


def target():
    tenant = demo.owner()
    application = demo.application(tenant, "stage10-checkout")
    return tenant, application, demo.credential(tenant, application)[1]


def verify_isolation(targets):
    first, second = targets
    event = stage.event(datetime.now(timezone.utc), "INFO", "Stage 10 shared identity")
    def saved(tenant):
        return fixture.query("select id from public.logs where "
                             f"organization_id='{UUID(tenant[1])}' and external_event_id='{UUID(event['eventId'])}'")
    ids = []
    for tenant, app, credential in targets:
        kafka.expect_v2(stage.batch(app, [event]), credential)
        ids.append(kafka.wait_for(lambda: saved(tenant), "Tenant event was not persisted", 35))
    demo.check(all(item.isdigit() for item in ids) and ids[0] != ids[1], "Tenant event identity collided")
    route = f"/api/v1/organizations/{first[0][1]}/observability/summary"
    demo.expect(404, demo.request("GET", route, token=second[0][0]), "Foreign summary")
    demo.expect(401, demo.request("GET", route, key=first[2]), "Ingestion credential on summary")
    route = f"/api/v1/logs/{ids[0]}"
    demo.expect(404, demo.request("GET", route, token=second[0][0], organization=second[0][1]), "Foreign log")
    return {"shared_event_id": "two independent persisted rows", "foreign_summary": 404,
            "foreign_log": 404, "ingestion_credential_on_summary": 401}


def browser_isolation(targets):
    from playwright.sync_api import sync_playwright, expect

    right, app, credential = targets[1]
    extra = stage.event(datetime.now(timezone.utc), "INFO", "Stage 10 additional browser event")
    stage.sync(app, credential, [extra])
    sizes = [1440, 768]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="chrome", headless=True)
        contexts = []
        for width, (tenant, _, _) in zip(sizes, targets):
            context = browser.new_context(viewport={"width":width,"height":940})
            context.add_init_script(f"localStorage.setItem('token',{json.dumps(tenant[0])});"
                                    f"localStorage.setItem('devinsight.activeOrganization',{json.dumps(tenant[1])});")
            page = context.new_page()
            page.goto("http://localhost:5173/dashboard", wait_until="domcontentloaded")
            contexts.append((context,page))
        for expected, (_,page) in enumerate(contexts,1):
            page.bring_to_front()
            card = page.get_by_role("link",name="Persisted logs",exact=False)
            expect(card.locator("strong")).to_have_text(str(expected),timeout=20000)
            demo.check(page.evaluate("document.documentElement.scrollWidth<=innerWidth"),"Dashboard viewport overflow")
        for context, _ in contexts:
            context.close()
        browser.close()
    return {"dashboard_log_counts": [1,2], "isolated_browser_contexts": 2, "viewport_widths": sizes}


def state(organization, tag):
    return tuple(map(int, fixture.query(f"""
        select count(*), count(distinct l.external_event_id),
          count(*) filter(where l.analysis_status='COMPLETED' and l.model_version='2.0.0-candidate'),
          count(distinct m.incident_id),count(m.log_id),count(distinct j.id),count(distinct n.id)
        from logs l left join incident_memberships m on m.organization_id=l.organization_id and m.log_id=l.id
        left join diagnosis_jobs j on j.organization_id=l.organization_id and j.log_id=l.id
        left join notification_outbox n on n.organization_id=l.organization_id and n.log_id=l.id
        where l.organization_id='{UUID(organization)}' and left(l.message,{len(tag)})='{tag}'
        """).split("|")))


def run_profile(directory, label, targets, count, batch_size, rate, mixed=False, blocked=False):
    tag = f"S10-{label}-{UUID(targets[0][0][1]).hex[:12]}"
    starts = kafka.kafka_status()
    sizes = [0 for _ in targets]
    accept, reads, memory = [], [], [resource_gate(directory)]
    stop = Event()

    def read_concurrently():
        user, organization = targets[0][0]
        while not stop.is_set():
            began = monotonic()
            status, data = demo.request("GET", f"/api/v1/organizations/{organization}/observability/summary", token=user)
            demo.check(status == 200 and "buckets" in data, "Dashboard read failed")
            reads.append(monotonic() - began)
            stop.wait(0.35)

    def current_state():
        return [state(item[0][1], tag) for item in targets]

    def all_persisted():
        rows = current_state()
        return rows if all(row[0] == total for row, total in zip(rows, sizes)) else None

    with ThreadPoolExecutor(max_workers=1) as pool:
        reader = pool.submit(read_concurrently) if mixed else None
        try:
            with hold_log_writes() if blocked else nullcontext():
                started = monotonic()
                for batch, first in enumerate(range(0, count, batch_size)):
                    sleep(max(0, started + first / rate - monotonic()))
                    index = batch % len(targets)
                    tenant, app, credential = targets[index]
                    entries = []
                    for i in range(first, min(count, first + batch_size)):
                        level = ("ERROR" if i % 7 == 0 else "WARN" if i % 3 == 0 else "INFO") if mixed else "INFO"
                        suffix = "PostgreSQL connection refused" if level == "ERROR" else "operational event"
                        entry = stage.event(datetime.now(timezone.utc), level, tag + " " + suffix)
                        entry["eventId"] = str(uuid5(UUID(tenant[1]), f"{label}-{i}"))
                        entries.append(entry)
                    body = stage.batch(app, entries)
                    tick = monotonic()
                    kafka.expect_v2(body, credential)
                    accept.append(monotonic() - tick)
                    sizes[index] += len(entries)
                    if batch % 8 == 0:
                        memory.append(resource_gate(directory))
                    demo.check(monotonic() - started < 90, "Submission time cap reached")
                accepted = monotonic()
                if blocked:
                    demo.check(current_state()[0][0] == 0, "Rows appeared under held write lock")
            released = monotonic()
            rows = kafka.wait_for(all_persisted, "Accepted records did not persist", 80)
            persisted = monotonic()
        finally:
            stop.set()
        if reader:
            reader.result(timeout=28)
    kafka.expect_v2(body, credential)
    def drained():
        broker = kafka.kafka_status()
        return broker if broker["lag"] == 0 else None
    after = kafka.wait_for(drained, "Broker lag did not drain", 55)
    demo.check(current_state() == rows, "Replay changed logical row counts")
    demo.check(after["dlqRecords"] == starts["dlqRecords"], "Unexpected DLQ change")
    demo.check(all(row[:3] == (total, total, total) for row, total in zip(rows, sizes)), "Unexpected persisted scoring")
    return dict(profile=label, events=count, requests=len(accept), failed_requests=0,
                accepted_s=round(accepted-started,3), persisted_s=round(persisted-started,3),
                persisted_per_s=round(count/(persisted-started),2), accept=timings(accept),
                dashboard=timings(reads), drain_after_unlock_s=round(persisted-released,3) if blocked else None,
                minimum_available_mib=min(memory), tenant_rows=rows, lag_after=0, dlq_delta=0)


def main(receipt):
    directory = Path(os.environ["STAGE08_FIXTURE_PGDATA"]).resolve()
    demo.check(os.environ.get("STAGE08_MANAGED") == "15438" and not receipt.exists()
               and directory.name == "pgdata-verified"
               and Path(fixture.query("select current_setting('data_directory')")).resolve() == directory,
               "Use the guarded disposable environment")
    targets = [target(), target()]
    cases = [("low",24,1,2), ("moderate",60,2,5), ("higher",112,4,15),
             ("burst",40,5,20), ("mixed",80,2,8), ("locked",20,5,12)]
    with receipt.open("x", encoding="utf-8") as output:
        auth = verify_isolation(targets)
        resource_gate(directory)
        output.write(json.dumps({"isolation": auth, "browser": browser_isolation(targets)}) + "\n")
        output.flush()
        for name, count, size, rate in cases:
            # Separate each profile's event budget and reserve room for its replay probe.
            applications = [target(), target()] if name == "mixed" else [target()]
            result = run_profile(directory, name, applications,
                                 count, size, rate, mixed=name == "mixed", blocked=name == "locked")
            print(json.dumps(result), flush=True)
            output.write(json.dumps(result) + "\n")
            output.flush()


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
