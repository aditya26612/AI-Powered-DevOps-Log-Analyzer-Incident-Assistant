"""
===============================================================================
DevInsight AI - Reproducible Model Training CLI Entrypoint
===============================================================================
Executes the model training workflow with full parameter configurability,
leakage-resistant data splitting, and verifiable artifact tracking.
===============================================================================
"""

import argparse
import sys
from pathlib import Path

# Ensure package importability
ml_root = Path(__file__).resolve().parents[2]
if str(ml_root) not in sys.path:
    sys.path.insert(0, str(ml_root))

from app.bootstrap.training import TrainingBootstrap
from app.core.training_config import DEFAULT_TRAINING_OUTPUT_DIR, TrainingConfig


def train(
    dataset_path: str = "data/synthetic_logs.json",
    feature_set: str = "curated",
    split_strategy: str = "template_grouped",
    test_size: float = 0.20,
    n_estimators: int = 300,
    contamination: float = 0.08,
    max_samples: int = 512,
    max_features: float = 0.85,
    random_state: int = 42,
    save_artifacts: bool = True,
    output_dir: str | Path = DEFAULT_TRAINING_OUTPUT_DIR,
    decision_threshold: float = 0.0,
):
    print("=" * 80)
    print("DevInsight Model Training Pipeline")
    print("=" * 80)
    print(f"Dataset         : {dataset_path}")
    print(f"Feature Set     : {feature_set}")
    print(f"Split Strategy  : {split_strategy} (test_size={test_size})")
    print(f"Estimators      : {n_estimators}")
    print(f"Contamination   : {contamination}")
    print(f"Decision Cutoff : {decision_threshold}")
    print(f"Random State    : {random_state}")
    print(f"Output Artifacts: {output_dir} (save={save_artifacts})")
    print("-" * 80)

    config = TrainingConfig(
        dataset_path=Path(dataset_path),
        enable_scaling=False if feature_set == "curated" else True,
        save_artifacts=save_artifacts,
        test_size=test_size,
        split_strategy=split_strategy,
        feature_set=feature_set,
        contamination=contamination,
        random_state=random_state,
        n_estimators=n_estimators,
        max_samples=max_samples,
        max_features=max_features,
        decision_threshold=decision_threshold,
        model_output_dir=Path(output_dir),
        artifacts_version="2.0.0-candidate" if feature_set == "curated" else "1.0.0",
        model_name="CuratedIsolationForest" if feature_set == "curated" else "IsolationForest",
    )

    pipeline = TrainingBootstrap.create(config)

    report = pipeline.run()

    print("\n" + "=" * 80)
    print("Training Completed Successfully")
    print("=" * 80)
    print(f"Total Samples          : {report.sample_count}")
    print(f"Features Evaluated     : {report.feature_count}")
    print(f"Anomalies Flagged      : {report.anomaly_count} ({report.anomaly_ratio * 100:.2f}%)")
    print(f"Training Time          : {report.training_time_seconds:.4f}s")
    if report.test_sample_count is not None:
        print(f"Test Partition Samples : {report.test_sample_count}")
        print(f"Test Precision         : {report.precision:.4f}" if report.precision is not None else "")
        print(f"Test Recall            : {report.recall:.4f}" if report.recall is not None else "")
        print(f"Test F1 Score          : {report.f1_score:.4f}" if report.f1_score is not None else "")
        print(f"Test PR-AUC            : {report.pr_auc:.4f}" if report.pr_auc is not None else "")
        print(f"False Alerts / 1k Logs : {report.false_alerts_per_1000}" if report.false_alerts_per_1000 is not None else "")
        print(f"Confusion Matrix       : {report.confusion_matrix}")
    print(f"Artifacts Persisted    : {report.artifacts_saved}")
    print("=" * 80)
    return report


def main():
    parser = argparse.ArgumentParser(description="DevInsight ML Pipeline Training Entrypoint")
    parser.add_argument("--dataset", type=str, default="data/synthetic_logs.json")
    parser.add_argument("--feature-set", type=str, default="curated", choices=["curated", "legacy", "no_severity"])
    parser.add_argument("--split-strategy", type=str, default="template_grouped", choices=["template_grouped", "stratified", "random"])
    parser.add_argument("--test-size", type=float, default=0.20)
    parser.add_argument("--n-estimators", type=int, default=300)
    parser.add_argument("--contamination", type=float, default=0.08)
    parser.add_argument("--max-samples", type=int, default=512)
    parser.add_argument("--max-features", type=float, default=0.85)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_TRAINING_OUTPUT_DIR)
    parser.add_argument("--decision-threshold", type=float, default=0.0)
    parser.add_argument("--no-save", action="store_true")

    args = parser.parse_args()

    train(
        dataset_path=args.dataset,
        feature_set=args.feature_set,
        split_strategy=args.split_strategy,
        test_size=args.test_size,
        n_estimators=args.n_estimators,
        contamination=args.contamination,
        max_samples=args.max_samples,
        max_features=args.max_features,
        random_state=args.seed,
        save_artifacts=not args.no_save,
        output_dir=args.output_dir,
        decision_threshold=args.decision_threshold,
    )


if __name__ == "__main__":
    main()
