"""
===============================================================================
DevInsight AI - Lightweight Operational Log Annotation Workflow
===============================================================================
Provides an audit-compliant review queue for human SRE annotation:
- Review logs from raw queues or model candidate feeds
- Assign operational labels: NORMAL, ANOMALOUS, UNCERTAIN
- Enforces rationale requirement and prevents silent auto-approval
- Tracks reviewer ID, confidence score, and timestamp
===============================================================================
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


VALID_LABELS = {"NORMAL", "ANOMALOUS", "UNCERTAIN"}


class AnnotationWorkflow:

    def __init__(self, queue_path: Path, output_path: Path, reviewer_id: str, dataset_version: str = "1.0.0"):
        self.queue_path = queue_path
        self.output_path = output_path
        self.reviewer_id = reviewer_id
        self.dataset_version = dataset_version

    def load_queue(self) -> list[dict]:
        if not self.queue_path.exists():
            return []
        items = []
        with open(self.queue_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    items.append(json.loads(line))
        return items

    def load_reviewed_ids(self) -> set[str]:
        if not self.output_path.exists():
            return set()
        reviewed = set()
        with open(self.output_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    data = json.loads(line)
                    if "log_id" in data:
                        reviewed.add(data["log_id"])
        return reviewed

    def annotate_batch(self, annotations: list[dict]) -> int:
        """Batch append reviewed annotations with audit validation."""
        reviewed_count = 0
        with open(self.output_path, "a", encoding="utf-8") as f:
            for item in annotations:
                label = item.get("ground_truth", "").upper()
                if label not in VALID_LABELS:
                    raise ValueError(f"Invalid label '{label}'. Must be one of {VALID_LABELS}")
                if not item.get("label_rationale"):
                    raise ValueError("Operational rationale is mandatory for every annotated entry.")

                # For binary classification: UNCERTAIN records have is_anomaly = None to prevent false assumption
                is_anom = True if label == "ANOMALOUS" else (False if label == "NORMAL" else None)
                review_status = "needs_adjudication" if label == "UNCERTAIN" else "human_verified"

                record = {
                    "log_id": item.get("log_id"),
                    "timestamp": item.get("timestamp"),
                    "level": item.get("level"),
                    "service_name": item.get("service_name") or item.get("log_source"),
                    "category": item.get("category", "unclassified"),
                    "message": item.get("message"),
                    "ground_truth": label,
                    "is_anomaly": is_anom,
                    "label_rationale": item["label_rationale"],
                    "label_source": item.get("label_source", "human_sre_annotator"),
                    "reviewer_id": self.reviewer_id,
                    "review_status": review_status,
                    "dataset_version": item.get("dataset_version", self.dataset_version),
                    "confidence": float(item.get("confidence", 1.0)),
                    "ambiguity_flag": (label == "UNCERTAIN"),
                    "annotated_at": datetime.now(timezone.utc).isoformat()
                }
                f.write(json.dumps(record) + "\n")
                reviewed_count += 1
        return reviewed_count

    def get_adjudicated_records(self) -> list[dict]:
        """Return only reviewed records excluding UNCERTAIN labels."""
        if not self.output_path.exists():
            return []
        records = []
        with open(self.output_path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    item = json.loads(line)
                    if item.get("ground_truth") in ("NORMAL", "ANOMALOUS") and item.get("is_anomaly") is not None:
                        records.append(item)
        return records

    def summarize(self) -> dict:
        total_queued = len(self.load_queue())
        reviewed_ids = self.load_reviewed_ids()
        labels_count = {"NORMAL": 0, "ANOMALOUS": 0, "UNCERTAIN": 0}
        if self.output_path.exists():
            with open(self.output_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip():
                        data = json.loads(line)
                        lbl = data.get("ground_truth", "UNCERTAIN")
                        labels_count[lbl] = labels_count.get(lbl, 0) + 1
        return {
            "total_queued": total_queued,
            "total_reviewed": len(reviewed_ids),
            "pending": max(0, total_queued - len(reviewed_ids)),
            "label_distribution": labels_count
        }


def main():
    parser = argparse.ArgumentParser(description="Operational Log Annotation Workflow")
    parser.add_argument("--queue", type=Path, default=Path("data/labeled_operational_logs.jsonl"))
    parser.add_argument("--output", type=Path, default=Path("data/annotated_review_store.jsonl"))
    parser.add_argument("--reviewer", type=str, default="sre-lead-01")
    parser.add_argument("--summary", action="store_true")
    args = parser.parse_args()

    workflow = AnnotationWorkflow(args.queue, args.output, args.reviewer)
    if args.summary:
        summary = workflow.summarize()
        print("\n=== Annotation Queue Summary ===")
        print(f"Total Queued Logs   : {summary['total_queued']}")
        print(f"Total Reviewed Logs : {summary['total_reviewed']}")
        print(f"Pending Reviews     : {summary['pending']}")
        print(f"Distribution        : {summary['label_distribution']}")
    else:
        print("Annotation workflow initialized. Use --summary or programmatic API.")


if __name__ == "__main__":
    main()
