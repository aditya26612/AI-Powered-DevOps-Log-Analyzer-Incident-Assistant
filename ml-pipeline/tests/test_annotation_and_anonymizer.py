import pytest
from pathlib import Path
from tools.log_anonymizer import LogAnonymizer
from tools.annotation_workflow import AnnotationWorkflow


def test_log_anonymizer_masks_pii():
    raw_msg = (
        "User bob@example.com logged in from 192.168.1.100 with card 4111 2222 3333 4444. "
        "Conn postgres://admin:SuperSecretPassword123@db.prod:5432/main. "
        "Session 16fbefb2-c71c-4f8a-919e-6e8582900fa2."
    )
    clean = LogAnonymizer.sanitize(raw_msg)

    assert "[REDACTED_EMAIL]" in clean
    assert "bob@example.com" not in clean
    assert "10.0.0.X" in clean
    assert "192.168.1.100" not in clean
    assert "[REDACTED_PAN]" in clean
    assert "4111 2222 3333 4444" not in clean
    assert "[REDACTED_SECRET]" in clean
    assert "SuperSecretPassword123" not in clean
    assert "[REDACTED_UUID]" in clean


def test_annotation_workflow_validation(tmp_path):
    q_file = tmp_path / "queue.jsonl"
    out_file = tmp_path / "reviewed.jsonl"

    workflow = AnnotationWorkflow(q_file, out_file, reviewer_id="sre-alice")

    # Invalid label raises ValueError
    with pytest.raises(ValueError, match="Invalid label"):
        workflow.annotate_batch([{
            "log_id": "1",
            "ground_truth": "BOGUS_LABEL",
            "label_rationale": "Test"
        }])

    # Missing rationale raises ValueError
    with pytest.raises(ValueError, match="Operational rationale is mandatory"):
        workflow.annotate_batch([{
            "log_id": "1",
            "ground_truth": "NORMAL",
            "label_rationale": ""
        }])

    # Valid entry succeeds
    count = workflow.annotate_batch([{
        "log_id": "1",
        "timestamp": "2026-11-19T10:00:00Z",
        "level": "INFO",
        "service_name": "user-service",
        "message": "Healthy log",
        "ground_truth": "NORMAL",
        "label_rationale": "Routine healthy event",
        "confidence": 1.0
    }])
    assert count == 1
    summary = workflow.summarize()
    assert summary["total_reviewed"] == 1
    assert summary["label_distribution"]["NORMAL"] == 1


def test_annotation_workflow_uncertain_handling(tmp_path):
    q_file = tmp_path / "queue.jsonl"
    out_file = tmp_path / "reviewed.jsonl"

    workflow = AnnotationWorkflow(q_file, out_file, reviewer_id="sre-alice", dataset_version="2.0.0")

    batch = [
        {
            "log_id": "u1",
            "timestamp": "2026-11-19T10:04:00Z",
            "level": "ERROR",
            "service_name": "api-gateway",
            "category": "database_failures",
            "message": "HTTP 500 error",
            "ground_truth": "UNCERTAIN",
            "label_rationale": "Single 500 error without context",
            "confidence": 0.5
        },
        {
            "log_id": "a1",
            "timestamp": "2026-11-19T10:04:05Z",
            "level": "FATAL",
            "service_name": "payment-service",
            "category": "database_failures",
            "message": "OOM error",
            "ground_truth": "ANOMALOUS",
            "label_rationale": "Metaspace exhausted",
            "confidence": 1.0
        }
    ]

    workflow.annotate_batch(batch)
    summary = workflow.summarize()
    assert summary["label_distribution"]["UNCERTAIN"] == 1
    assert summary["label_distribution"]["ANOMALOUS"] == 1

    # Adjudicated records should exclude UNCERTAIN
    adjudicated = workflow.get_adjudicated_records()
    assert len(adjudicated) == 1
    assert adjudicated[0]["log_id"] == "a1"
    assert adjudicated[0]["is_anomaly"] is True

    # Check that UNCERTAIN record stored has is_anomaly = None and review_status = needs_adjudication
    import json
    with open(out_file, "r", encoding="utf-8") as f:
        stored = [json.loads(line) for line in f if line.strip()]
    u_rec = next(r for r in stored if r["log_id"] == "u1")
    assert u_rec["is_anomaly"] is None
    assert u_rec["ambiguity_flag"] is True
    assert u_rec["review_status"] == "needs_adjudication"
    assert u_rec["category"] == "database_failures"
    assert u_rec["dataset_version"] == "2.0.0"
