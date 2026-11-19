"""
Empirical Threshold & False-Alert Tradeoff Validation.
Evaluates V2 Curated Isolation Forest on development/validation data across operating points.
"""

import sys
import json
from pathlib import Path
from datetime import datetime, timezone

_cur_dir = Path(__file__).resolve().parent
_root = _cur_dir.parent
if str(_root) not in sys.path:
    sys.path.insert(0, str(_root))

from app.bootstrap.prediction import PredictionBootstrap
from app.domain.log_entry import LogEntry


def run_threshold_validation():
    ps = PredictionBootstrap.create()

    if ps.metadata.feature_version != "2.0.0":
        raise ValueError("Threshold validation requires the V2 model")

    data_path = _root / "data/labeled_operational_logs.jsonl"
    if not data_path.exists():
        data_path = Path("data/labeled_operational_logs.jsonl")
    if not data_path.exists():
        print(f"Error: {data_path} not found.")
        return

    records = []
    with open(data_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))

    # Exclude ambiguous labels (is_anomaly is None)
    eval_records = [r for r in records if r.get("is_anomaly") is not None]
    n_pos = sum(1 for r in eval_records if r["is_anomaly"] is True)
    n_neg = sum(1 for r in eval_records if r["is_anomaly"] is False)

    log_entries = [
        LogEntry(
            timestamp=datetime.fromisoformat(r["timestamp"].replace("Z", "+00:00")),
            level=r.get("level", "INFO"),
            service_name=r.get("service_name", "unknown"),
            message=r.get("message", ""),
        )
        for r in eval_records
    ]

    feat_matrix = ps._feature_builder.build(log_entries)
    scores = ps._model.decision_function(feat_matrix)

    thresholds = [-0.06, -0.05, -0.04, -0.03, -0.02, -0.01, 0.00, 0.01, 0.02, 0.03, 0.04, 0.05]
    metrics = []

    for t in thresholds:
        tp = fp = tn = fn = 0
        for r, sc in zip(eval_records, scores):
            actual = r["is_anomaly"] is True
            # In IsolationForest, lower decision score = more anomalous.
            # Anomaly predicted if score < threshold
            pred = float(sc) < t

            if pred and actual:
                tp += 1
            elif pred and not actual:
                fp += 1
            elif not pred and not actual:
                tn += 1
            else:
                fn += 1

        prec = tp / (tp + fp) if (tp + fp) > 0 else 1.0
        rec = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (2 * prec * rec / (prec + rec)) if (prec + rec) > 0 else 0.0
        fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
        fp_1k_norm = fpr * 1000.0
        fp_1k_tot = (fp / len(eval_records)) * 1000.0

        metrics.append({
            "threshold": t,
            "tp": tp,
            "fp": fp,
            "tn": tn,
            "fn": fn,
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "fpr": round(fpr, 4),
            "fp_per_1000_normal": round(fp_1k_norm, 1),
            "fp_per_1000_total": round(fp_1k_tot, 1),
        })

    output = {
        "dataset": "labeled_operational_logs.jsonl",
        "dataset_type": "development_validation_operational_sample",
        "total_records": len(eval_records),
        "anomalies": n_pos,
        "normal": n_neg,
        "model_version": ps.metadata.model_version,
        "feature_count": ps.metadata.feature_count,
        "threshold_evaluations": metrics,
    }

    out_file = _root / "artifacts/threshold_validation_results.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)

    print(f"Validation complete across {len(thresholds)} operating points.")
    print(f"Results written to {out_file}")
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    run_threshold_validation()
