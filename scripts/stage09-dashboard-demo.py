"""Verify the observability workspace against a guarded local Kafka/PostgreSQL fixture."""
from datetime import datetime, timezone
from importlib.util import module_from_spec, spec_from_file_location
import base64
import json
import os
from pathlib import Path
import secrets
import statistics
import sys
from time import perf_counter
from uuid import UUID, uuid4
from urllib.parse import urlsplit

from playwright.sync_api import expect, sync_playwright


def load_script(name):
    spec = spec_from_file_location(name,Path(__file__).with_name(name + ".py"))
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


flow = load_script("stage08-notification-demo")
demo, stage, kafka = flow.demo, flow.stage, flow.kafka
diagnosis = load_script("stage07-diagnosis-demo")
web = "http://localhost:5173"


def register():
    email = "stage09-" + uuid4().hex + "@example.invalid"
    password = "DevInsight09!" + secrets.token_hex(14)
    request = {"email":email,"password":password}
    demo.expect(200,demo.request("POST","/api/v1/auth/register",request),"Fixture registration")
    token = demo.expect(200,demo.request("POST","/api/v1/auth/login",request),"Fixture login")["token"]
    claims = json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "==="))
    return email,password,token,claims["user_id"]


def organization(actor, label):
    token = actor[2]
    body = {"name":label,"slug":"s09-" + uuid4().hex}
    value = demo.expect(201,demo.request("POST","/api/v1/organizations",body,token=token),"Fixture organization")
    return token,value["id"]


def read(tenant, route, expected=200):
    return demo.expect(expected,demo.request("GET",f"/api/v1/organizations/{tenant[1]}/{route}",
                                          token=tenant[0]),"Scoped workspace read")


def change_log_state(tenant, log_id, statement):
    """Prepare classification cases using one of two fixed synthetic fixture updates."""
    commands = {
        "failed": "analysis_status='FAILED', anomaly=false, model_version=null, decision_score=null, "
                  "anomaly_score=null, prediction=null, prediction_label=null",
        "anomaly": "analysis_status='COMPLETED', anomaly=true, model_version='2.0.0-candidate', decision_score=-0.05, "
                   "anomaly_score=-0.05, prediction=-1, prediction_label='Anomaly'",
    }
    result = flow.subprocess.run([*flow.psql(),"-c",f"""
        begin read write;
        update public.logs set {commands[statement]}
        where organization_id='{UUID(tenant[1])}' and id={int(log_id)}
            and exists(select 1 from public.organizations where id='{UUID(tenant[1])}' and slug like 's09-%')
        returning id;
        commit;
        """],env=flow.database_env(),capture_output=True,text=True,timeout=8,
        creationflags=flow.subprocess.CREATE_NO_WINDOW)
    demo.check(result.returncode==0 and result.stdout.strip()==str(log_id),"Synthetic scoring-state fixture failed")


def choose(page, tenant):
    page.get_by_role("combobox",name="Organization",exact=True).select_option(tenant[1])


def live_run(primary, app, key, directory, receiver, page):
    incident, trace = flow.collect(primary,app,key)
    page.goto(web + "/incidents?incident=" + incident)
    detail = page.get_by_role("region",name="Incident details")
    job = detail.get_by_role("region",name="AI diagnosis")
    slack = detail.get_by_role("region",name="Slack notification")
    expect(job.get_by_text("Pending",exact=True)).to_be_visible(timeout=15000)
    os.environ["STAGE07_INTERNAL_KEY"] = os.environ["STAGE08_INTERNAL_KEY"]
    with diagnosis.Relay().running() as relay:
        relay.mode = "hold_result"
        with flow.worker(directory,receiver,diagnose=True,deliver=False,llm_url=relay.url):
            expect(job.get_by_text("Running",exact=True)).to_be_visible(timeout=80000)
            demo.check(relay.returned.wait(105),"Real Ollama did not return through the controlled relay")
            relay.released.set()
            expect(job.get_by_text("Completed",exact=True)).to_be_visible(timeout=20000)
    with flow.worker(directory,receiver,diagnose=False,deliver=True):
        delivery = flow.wait_status(primary,incident,"DELIVERED",85)
        expect(slack.get_by_text("Delivered",exact=True)).to_be_visible(timeout=12000)
    slack.get_by_role("button",name="Attempt history").click()
    expect(slack.get_by_text("HTTP 200",exact=False)).to_be_visible()
    demo.check(receiver.count(UUID(delivery["id"]))==1,"Primary incident produced more than one send")
    return incident, trace


def delivery_recovery(primary, app, key, directory, receiver, page, chrome, viewer):
    incident = flow.seed(primary,app,key)
    page.goto(web + "/incidents?incident=" + incident)
    job = page.get_by_role("region",name="AI diagnosis")
    slack = page.get_by_role("region",name="Slack notification")
    with diagnosis.Relay().running() as relay:
        relay.mode = "outage"
        with flow.worker(directory,receiver,diagnose=True,deliver=False,llm_url=relay.url,
                         diagnosis_delay="7s"):
            expect(job.get_by_text("Retry scheduled",exact=True)).to_be_visible(timeout=45000)
            expect(job.get_by_text("Terminal failure",exact=True)).to_be_visible(timeout=80000)
        viewer_page = chrome.new_page()
        viewer_page.add_init_script(f"localStorage.setItem('token',{json.dumps(viewer[2])});"
                                    f"localStorage.setItem('devinsight.activeOrganization',{json.dumps(primary[1])});")
        viewer_page.goto(web + "/incidents?incident=" + incident)
        expect(viewer_page.get_by_text("Terminal failure",exact=True)).to_be_visible()
        expect(viewer_page.get_by_role("button",name="Retry diagnosis")).to_have_count(0)
        expect(viewer_page.get_by_role("button",name="Acknowledge")).to_have_count(0)
        relay.mode = "forward"
        page.bring_to_front()
        job.get_by_role("button",name="Retry diagnosis",exact=True).click()
        with flow.worker(directory,receiver,diagnose=True,deliver=False,llm_url=relay.url):
            expect(job.get_by_text("Completed",exact=True)).to_be_visible(timeout=140000)
    pending = flow.wait_status(primary,incident,"PENDING",15)
    receiver.hold(pending["id"])
    with flow.worker(directory,receiver,diagnose=False,deliver=True):
        demo.check(receiver.observed.wait(75),"No controlled notification attempt was received")
    receiver.release.set()
    with flow.worker(directory,receiver,diagnose=False,deliver=True):
        expect(slack.get_by_text("Delivery unknown",exact=True)).to_be_visible(timeout=85000)
    viewer_page.bring_to_front()
    expect(viewer_page.get_by_text("Delivery unknown",exact=True)).to_be_visible(timeout=16000)
    expect(viewer_page.get_by_role("button",name="Retry Slack notification")).to_have_count(0)
    page.bring_to_front()
    with page.expect_event("dialog") as confirmation:
        page.once("dialog",lambda dialog: dialog.dismiss())
        slack.get_by_role("button",name="Retry Slack notification",exact=True).click()
    demo.check("already have accepted" in confirmation.value.message,"Retry confirmation omitted uncertainty")
    expect(slack.get_by_text("Delivery unknown",exact=True)).to_be_visible()
    route = f"**/notifications/{pending['id']}/retry"
    with flow.worker(directory,receiver,diagnose=False,deliver=False) as (_,log):
        port = flow.worker_port(log)
        def retry_real_fixture(intercept):
            request = intercept.request
            response = page.request.post("http://127.0.0.1:" + str(port) + urlsplit(request.url).path,
                                         headers={"Authorization":"Bearer " + primary[0],"Content-Type":"application/json"},
                                         data=request.post_data,
                                         timeout=12000)
            intercept.fulfill(status=response.status,body=response.body(),
                              headers={"Content-Type":"application/json","Access-Control-Allow-Origin":web})
        page.route(route,retry_real_fixture)
        with page.expect_response(lambda response: response.url.endswith("/retry") and response.status==202):
            page.once("dialog",lambda dialog: dialog.accept())
            slack.get_by_role("button",name="Retry Slack notification",exact=True).click()
        page.unroute(route)
    with flow.worker(directory,receiver,diagnose=False,deliver=True):
        expect(slack.get_by_text("Delivered",exact=True)).to_be_visible(timeout=85000)
    demo.check(receiver.count(UUID(pending["id"]))==2,"Explicit confirmation did not demonstrate possible duplication")
    viewer_page.close()


def browser(output):
    fixture = Path(os.environ["STAGE08_FIXTURE_PGDATA"]).resolve()
    demo.check(os.environ.get("STAGE08_MANAGED")=="15438" and not output.exists()
               and fixture.name=="pgdata-verified"
               and Path(flow.query("select current_setting('data_directory')")).resolve()==fixture,
               "Use the guarded disposable PostgreSQL fixture")
    snapshots = output.parent / (output.stem + "-screenshots")
    snapshots.mkdir()
    label = uuid4().hex[:5]
    actor, viewer = register(),register()
    primary = organization(actor,"Checkout operations " + label)
    empty = organization(actor,"Greenfield " + label)
    foreign = organization(viewer,"Other operations " + label)
    primary_app = demo.application(primary,"checkout-api")
    app_id = int(primary_app[1].split("/")[-2])
    peer = demo.application(primary,"payments-worker")
    foreign_app = demo.application(foreign,"checkout-api")
    foreign_app_id = int(foreign_app[1].split("/")[-2])
    key = demo.credential(primary,primary_app)[1]
    demo.credential(foreign,foreign_app)
    demo.expect(204,demo.request("PUT",primary_app[1].removesuffix("/credentials") + "/slack",
                {"url":"https://hooks.slack.com/services/FIXTURE/NOT_REAL/" + uuid4().hex},token=primary[0]),
                "Configure fixture delivery")
    demo.expect(204,demo.request("PUT",f"/api/v1/organizations/{primary[1]}/members/{viewer[3]}",
                {"role":"VIEWER"},token=primary[0]),"Fixture viewer")
    read(foreign,f"observability/summary?applicationId={app_id}",404)
    read(primary,f"observability/summary?applicationId={foreign_app_id}",404)
    demo.expect(404,demo.request("GET",f"/api/v1/organizations/{primary[1]}/observability/summary",
                                token=register()[2]),"Foreign organization")
    errors = []
    page_requests = []
    with sync_playwright() as playwright, flow.Receiver().running() as receiver:
        chrome = playwright.chromium.launch(channel="chrome",headless=True)
        page = chrome.new_page(viewport={"width":1440,"height":1000})
        page.on("pageerror",lambda failure: errors.append(type(failure).__name__))
        page.on("console",lambda message: errors.append("React warning")
                if message.type in ("error","warning") and ("React" in message.text or "key" in message.text) else None)
        page.on("request",lambda request: page_requests.append(request.method)
                if urlsplit(request.url).netloc in ("localhost:8080","127.0.0.1:8080") else None)
        page.goto(web + "/login")
        page.get_by_label("Email").fill(actor[0])
        page.get_by_label("Password").fill(actor[1])
        page.get_by_role("button",name="Sign in").click()
        expect(page.get_by_role("heading",name="Overview",exact=True)).to_be_visible()
        held = []
        summary_pattern = "**/observability/summary?*"
        page.route(summary_pattern,lambda route: held.append(route),times=1)
        choose(page,empty)
        expect(page.get_by_text("Loading observability metrics…",exact=True)).to_be_visible()
        kafka.wait_for(lambda: held or (page.wait_for_timeout(30) or None),"The slow overview request did not start",8)
        held[0].continue_()
        expect(page.get_by_text("No logs in this time range.")).to_be_visible()
        expect(page.get_by_text("No unresolved incidents in this range.")).to_be_visible()
        choose(page,primary)
        incident, trace = live_run(primary,primary_app,key,output.parent,receiver,page)
        peer_key = demo.credential(primary,peer)[1]
        peer_event = stage.event(datetime.now(timezone.utc),"INFO","Fixture related span")
        peer_event["traceparent"] = f"00-{trace}-fabe111111111111-01"
        stage.sync(peer,peer_key,[peer_event])
        batch = [stage.event(datetime.now(timezone.utc),"WARN" if i==3 else "INFO",
                            f"Pagination fixture marker {i:02}") for i in range(28)]
        ids = []
        for offset in range(0,len(batch),10):
            ids.extend(stage.sync(primary_app,key,batch[offset:offset+10]))
        change_log_state(primary,ids[0],"failed")
        change_log_state(primary,ids[1],"anomaly")
        page.reload()
        detail = page.get_by_role("region",name="Incident details")
        expect(detail.get_by_role("heading",name="Correlation evidence")).to_be_visible(timeout=12000)
        expect(detail.get_by_text("payments-worker",exact=False).first).to_be_visible()
        detail.get_by_role("button",name="Acknowledge").click()
        expect(detail.get_by_text("Acknowledged",exact=True).first).to_be_visible()
        detail.get_by_role("button",name="Resolve").click()
        expect(detail.get_by_text("Resolved",exact=True).first).to_be_visible()
        page.screenshot(path=str(snapshots/"incident.png"),full_page=True)
        detail.get_by_role("link",name="Open in log explorer").click()
        expect(page.get_by_role("table")).to_be_visible()
        page.get_by_role("link",name="Logs",exact=True).click()
        page.get_by_role("searchbox",name="Search message").fill("Pagination fixture marker")
        page.get_by_role("button",name="Apply search").click()
        next_page = page.get_by_role("button",name="Next",exact=True)
        expect(next_page).to_be_enabled()
        next_page.click()
        expect(page.get_by_text("Page 2",exact=True)).to_be_visible()
        expect(page.get_by_role("table").get_by_role("row")).to_have_count(4)
        page.get_by_role("button",name="Previous",exact=True).click()
        expect(page.get_by_role("table").get_by_role("row")).to_have_count(26)
        page.get_by_role("combobox",name="ML status").select_option("FAILED")
        expect(page.get_by_text("Unavailable",exact=True)).to_be_visible()
        expect(page.get_by_role("table").get_by_text("Failed",exact=True)).to_be_visible()
        page.get_by_role("combobox",name="ML status").select_option("")
        page.get_by_role("combobox",name="Classification").select_option("V2_ANOMALY")
        expect(page.get_by_role("table").get_by_text("V2 anomaly",exact=True)).to_be_visible()
        page.get_by_role("link",name="Pagination fixture marker 01").click()
        expect(page.get_by_text("Correlation ID",exact=True)).to_be_visible()
        page.get_by_text("Original event",exact=True).click()
        expect(page.get_by_text("Pagination fixture marker 01",exact=False).first).to_be_visible()
        delivery_recovery(primary,primary_app,key,output.parent,receiver,page,chrome,viewer)
        page.goto(web + "/dashboard")
        expect(page.get_by_role("heading",name="Event volume")).to_be_visible()
        page.get_by_role("combobox",name="Time range").select_option("WEEK")
        page.get_by_role("combobox",name="Application").select_option(str(app_id))
        expect(page.get_by_role("heading",name="Event volume")).to_be_visible()
        page.get_by_role("button",name="View values").click()
        expect(page.get_by_role("region",name="Event volume values")).to_be_visible()
        page.get_by_role("button",name="Hide values").click()
        page.screenshot(path=str(snapshots/"overview-desktop.png"),full_page=True)
        page.set_viewport_size({"width":390,"height":844})
        demo.check(page.evaluate("document.documentElement.scrollWidth === innerWidth"),"Mobile overview overflow")
        page.screenshot(path=str(snapshots/"overview-mobile.png"),full_page=True)
        page.get_by_role("link",name="Applications",exact=True).click()
        page.get_by_role("combobox",name="Application",exact=True).select_option(str(app_id))
        expect(page.get_by_text("A webhook is configured.",exact=False)).to_be_visible()
        page.get_by_role("link",name="Overview",exact=True).click()
        choose(page,empty)
        expect(page.get_by_text("No logs in this time range.")).to_be_visible()
        expect(page.get_by_text("checkout-api",exact=True)).to_have_count(0)
        choose(page,primary)
        expect(page.get_by_role("heading",name="Event volume")).to_be_visible()
        page.get_by_role("link",name="Incidents",exact=True).focus()
        demo.check(page.evaluate("document.activeElement.textContent.includes('Incidents')"),"Keyboard focus missing")
        with page.expect_response(lambda response: "/observability/summary" in response.url):
            page.get_by_role("button",name="Refresh",exact=True).click()
        before_error = len(page_requests)
        page.route("**/observability/summary?*",lambda route: route.fulfill(status=503,body="{}",
                   headers={"Access-Control-Allow-Origin":web,"Content-Type":"application/json"}))
        page.get_by_role("button",name="Refresh",exact=True).click()
        expect(page.get_by_text("Previously loaded data is shown.",exact=False)).to_be_visible()
        page.unroute_all(behavior="wait")
        page.get_by_role("button",name="Refresh",exact=True).click()
        expect(page.get_by_role("alert")).to_have_count(0)
        page.evaluate("localStorage.setItem('token','expired-test-session')")
        page.reload()
        expect(page.get_by_role("heading",name="Welcome back")).to_be_visible(timeout=12000)
        timing = {}
        for name, route in {
            "overview":"observability/summary?range=DAY","logs":"observability/logs?range=DAY&size=25",
            "incidents":"incidents?size=25","incident_detail":"incidents/" + incident,
        }.items():
            samples = []
            for _ in range(6):
                started = perf_counter()
                read(primary,route)
                samples.append((perf_counter()-started)*1000)
            timing[name] = round(statistics.median(samples[1:]),2)
        chrome.close()
    summary = read(primary,"observability/summary?range=DAY")
    start, end = [datetime.fromisoformat(summary[field]).astimezone(timezone.utc).replace(tzinfo=None).isoformat()
                  for field in ("from","to")]
    scope = f"organization_id='{UUID(primary[1])}' and timestamp>='{start}' and timestamp<'{end}'"
    actual = [int(value) for value in flow.query(f"""
        select count(*), count(*) filter (where analysis_status='COMPLETED' and model_version='2.0.0-candidate' and anomaly),
        count(*) filter (where level in ('ERROR','FATAL')),count(*) filter (where analysis_status='FAILED')
        from public.logs where {scope}""").split("|")]
    totals = [sum(bucket[field] for bucket in summary["buckets"])
              for field in ("logs","anomalies","errors","scoringFailures")]
    demo.check(totals==actual and totals[1]>=1 and totals[3]>=1,"Dashboard does not reconcile with scoped PostgreSQL totals")
    plan_text = flow.query(f"""
        begin read only;
        set local role devinsight_log_tenant;
        set local devinsight.organization_id='{UUID(primary[1])}';
        explain (analyze,buffers,format json)
        select date_bin(interval '1 hour',timestamp,timestamp '1970-01-01') as bucket,level,count(*)
        from public.logs where {scope} group by bucket,level;
        rollback;
        """)
    plan = json.loads(plan_text)[0]
    def node_types(node):
        return [node["Node Type"],*(kind for child in node.get("Plans",[]) for kind in node_types(child))]
    demo.check(not errors,"Chrome reported a runtime or React warning")
    with output.open("x",encoding="utf-8") as receipt:
        json.dump(dict(real_collector_kafka_ollama_receiver=True,browser_workflow=True,scoped_totals=totals,
                       median_gateway_latency_ms=timing,browser_gateway_requests=len(page_requests),
                       requests_through_first_overview_refresh=before_error,
                       role_checks=True,zero_data=True,diagnosis_retry_states=True,notification_unknown_confirmed_retry=True,
                       rollup_sql_ms=plan["Execution Time"],rollup_plan_nodes=node_types(plan["Plan"]),
                       rejected_session=True,held_loading_state=True,mobile_width=390,desktop_width=1440),receipt,indent=2)
    print("Verified the live observability browser workflow and scoped PostgreSQL totals",flush=True)


if __name__ == "__main__":
    browser(Path(sys.argv[1]).resolve())
