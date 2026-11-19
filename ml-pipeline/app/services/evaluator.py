"""
===============================================================================
DevInsight AI - Evaluator Service
===============================================================================

Evaluates a trained anomaly detection model.

Responsibilities
----------------
- Compute anomaly statistics
- Produce a TrainingReport

This service performs no training and no artifact management.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import numpy as np

from app.domain.training_report import TrainingReport
from app.domain.training_result import TrainingResult


class Evaluator:
    """
    Evaluates a trained Isolation Forest model.
    """

    @staticmethod
    def evaluate(
        result: TrainingResult,
        predictions: np.ndarray,
        artifacts_saved: bool,
        y_true: np.ndarray | None = None,
        decision_scores: np.ndarray | None = None,
    ) -> TrainingReport:
        """
        Produce the final training report.
        """

        anomaly_count = int(np.sum(predictions == -1))

        anomaly_ratio = anomaly_count / len(predictions) if len(predictions) > 0 else 0.0

        prec = None
        rec = None
        f1 = None
        f2 = None
        roc = None
        pr = None
        fpr = None
        fa_per_1k = None
        cm_dict = None

        if y_true is not None and len(y_true) > 0:
            # Convert IsolationForest convention (-1: anomalous, 1: normal) to binary (1: anomaly, 0: normal)
            y_pred = (predictions == -1).astype(int)
            y_true_binary = (y_true == 1).astype(int)

            tp = int(np.sum((y_pred == 1) & (y_true_binary == 1)))
            fp = int(np.sum((y_pred == 1) & (y_true_binary == 0)))
            fn = int(np.sum((y_pred == 0) & (y_true_binary == 1)))
            tn = int(np.sum((y_pred == 0) & (y_true_binary == 0)))

            prec = round(float(tp / (tp + fp)), 4) if (tp + fp) > 0 else 0.0
            rec = round(float(tp / (tp + fn)), 4) if (tp + fn) > 0 else 0.0
            f1 = round(float(2 * tp / (2 * tp + fp + fn)), 4) if (2 * tp + fp + fn) > 0 else 0.0
            f2 = round(float(5 * tp / (5 * tp + 4 * fn + fp)), 4) if (5 * tp + 4 * fn + fp) > 0 else 0.0
            fpr = round(float(fp / (fp + tn)), 4) if (fp + tn) > 0 else 0.0
            fa_per_1k = round(float((fp / len(y_true_binary)) * 1000), 2)
            cm_dict = {"tp": tp, "fp": fp, "fn": fn, "tn": tn}

            if decision_scores is not None:
                try:
                    from sklearn.metrics import roc_auc_score, average_precision_score
                    # Anomaly score direction: higher score -> higher anomaly likelihood
                    roc = round(float(roc_auc_score(y_true_binary, decision_scores)), 4)
                    pr = round(float(average_precision_score(y_true_binary, decision_scores)), 4)
                except Exception:
                    pass

        return TrainingReport(
            sample_count=result.sample_count,
            feature_count=result.feature_count,
            anomaly_count=anomaly_count,
            anomaly_ratio=round(anomaly_ratio, 6),
            training_time_seconds=result.training_time_seconds,
            artifacts_saved=artifacts_saved,
            precision=prec,
            recall=rec,
            f1_score=f1,
            f2_score=f2,
            roc_auc=roc,
            pr_auc=pr,
            false_positive_rate=fpr,
            false_alerts_per_1000=fa_per_1k,
            test_sample_count=len(y_true) if y_true is not None else None,
            confusion_matrix=cm_dict,
        )