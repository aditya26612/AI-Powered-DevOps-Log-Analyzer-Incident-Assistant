"""Run bounded, identity-checked faults against the disposable Stage 10 stack."""
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import sys
from uuid import UUID, uuid4

spec = importlib.util.spec_from_file_location("fixture", Path(__file__).with_name("stage08-notification-demo.py"))
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
demo, stage, kafka = fixture.demo, fixture.stage, fixture.kafka


def persisted(tenant, item):
    return fixture.query("select id from public.logs where "
                         f"organization_id='{UUID(tenant[1])}' and external_event_id='{UUID(item['eventId'])}'")


def restart_apps():
    kafka.run_ps("stop-all.ps1")
    kafka.run_ps("start-all.ps1", "-Kafka", "-KafkaFixture", "-DatabasePort", "15438")


def retry_broker_receipt(body, key):
    response = demo.request("POST", kafka.V2, body, key=key)
    if response[0] == 503:
        return None
    receipt = demo.expect(202, response, "Kafka recovery")
    demo.check(receipt["acknowledgement"] == "BROKER_ACCEPTED"
               and [row["eventId"] for row in receipt["events"]] == [event["eventId"] for event in body["events"]],
               "Kafka recovery did not acknowledge the original event")
    return receipt


def diagnosis_recovery(output):
    spec = importlib.util.spec_from_file_location("diagnosis_relay", Path(__file__).with_name("stage07-diagnosis-demo.py"))
    relay_module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(relay_module)
    os.environ["STAGE07_INTERNAL_KEY"] = os.environ["STAGE08_INTERNAL_KEY"]
    tenant = demo.owner()
    app = demo.application(tenant,"stage10-diagnosis")
    key = demo.credential(tenant,app)[1]
    marker = "Stage 10 database pool connection refused " + uuid4().hex
    incident = relay_module.seed(tenant,app,key,marker)
    before = relay_module.view(tenant,incident)
    demo.check(before["status"] == "PENDING", "Fixture diagnosis was already claimed")
    with relay_module.Relay().running() as relay, fixture.Receiver().running() as receiver:
        relay.marker = marker
        relay.mode = "hold_request"
        with fixture.worker(output.parent,receiver,diagnose=True,deliver=False,llm_url=relay.url,diagnosis_lease="12s"):
            demo.check(relay.request_held.wait(90), "Diagnosis worker did not enter controlled inference")
            active = relay_module.view(tenant,incident)
            demo.check(active["id"] == before["id"] and active["status"] == "RUNNING",
                       "Diagnosis did not hold the expected claim")
        relay.unblock_request.set()
        relay.mode = "forward"
        with fixture.worker(output.parent,receiver,diagnose=True,deliver=False,llm_url=relay.url,diagnosis_lease="12s"):
            accepted = relay_module.await_job(tenant,incident,"SUCCEEDED",135)
        demo.check(accepted["id"] == before["id"]
                   and [item["outcome"] for item in accepted["attempts"]] == ["SUCCEEDED","LEASE_EXPIRED"],
                   "Worker recovery did not preserve attempt history")
        return {"same_job": True, "attempts": ["LEASE_EXPIRED","SUCCEEDED"], "real_inference": True}


def main(output):
    directory = Path(os.environ["STAGE08_FIXTURE_PGDATA"]).resolve()
    demo.check(os.environ.get("STAGE08_MANAGED") == "15438" and not output.exists()
               and os.environ["DEVINSIGHT_KAFKA_FIXTURE_NAME"].startswith("fixture-stage10-")
               and Path(fixture.query("select current_setting('data_directory')")).resolve() == directory,
               "Use the guarded disposable Stage 10 stack")
    tenant = demo.owner()
    app = demo.application(tenant, "stage10-chaos")
    key = demo.credential(tenant, app)[1]
    event = stage.event(datetime.now(timezone.utc), "INFO", "Stage 10 Kafka outage item")
    body = stage.batch(app, [event])
    kafka.run_ps("stop-kafka-local.ps1", "-Fixture")
    try:
        demo.expect(503, demo.request("POST", kafka.V2, body, key=key), "Kafka outage")
        demo.check(not persisted(tenant,event), "Unavailable broker produced a persisted event")
        stage.sync(app,key,[stage.event(datetime.now(timezone.utc),"INFO","Synchronous delivery during broker outage")])
    finally:
        kafka.run_ps("start-kafka-local.ps1", "-Fixture")
    kafka.wait_for(lambda: retry_broker_receipt(body, key), "Kafka did not resume accepting requests", 70)
    kafka.wait_for(lambda: persisted(tenant,event),"Kafka retry did not persist",45)
    print("Verified Kafka outage and recovery", flush=True)

    kafka.run_ps("stage10-pause-service.ps1","-Service","ml-pipeline")
    try:
        outage = stage.sync(app,key,[stage.event(datetime.now(timezone.utc),"ERROR","Stage 10 ML unavailable")])[0]
        stopped = stage.row(tenant,outage)
        demo.check(stopped["analysisStatus"] == "FAILED" and stopped.get("modelVersion") is None,
                   "ML outage fabricated a scoring result")
    finally:
        restart_apps()
    complete = stage.sync(app,key,[stage.event(datetime.now(timezone.utc),"INFO","Stage 10 ML recovered")])[0]
    result = stage.row(tenant,complete)
    demo.check(result["analysisStatus"] == "COMPLETED" and result["modelVersion"] == "2.0.0-candidate",
               "Recovered ML did not score")

    during_gateway = stage.event(datetime.now(timezone.utc),"INFO","Stage 10 Gateway retry item")
    pending = stage.batch(app,[during_gateway])
    kafka.run_ps("stage10-pause-service.ps1","-Service","api-gateway")
    try:
        try:
            demo.request("POST", kafka.V2, pending, key=key)
        except OSError:
            pass
        else:
            raise RuntimeError("Fixture Gateway still returned HTTP after stop")
        demo.check(not persisted(tenant,during_gateway), "Unavailable Gateway accepted an event")
    finally:
        restart_apps()
    kafka.expect_v2(pending,key)
    kafka.expect_v2(pending,key)
    kafka.wait_for(lambda: persisted(tenant,during_gateway),"Gateway retry did not persist",45)
    kafka.wait_for(lambda: kafka.kafka_status()["lag"] == 0,"Recovery backlog remained",45)
    diagnosis = diagnosis_recovery(output)

    with output.open("x",encoding="utf-8") as receipt:
        json.dump(dict(kafka_outage_http=503, synchronous_during_kafka_outage=200, recovered_kafka_http=202,
                       ml_outage_status="FAILED", recovered_ml_status="COMPLETED",
                       gateway_unavailable_then_replayed=True, final_kafka_lag=0, diagnosis=diagnosis),receipt,indent=2)
    print("Verified isolated Gateway, Kafka, and ML failures and recovery",flush=True)


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
