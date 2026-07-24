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
    ) -> TrainingReport:
        """
        Produce the final training report.
        """

        anomaly_count = int(np.sum(predictions == -1))

        anomaly_ratio = anomaly_count / len(predictions)

        return TrainingReport(
            sample_count=result.sample_count,
            feature_count=result.feature_count,
            anomaly_count=anomaly_count,
            anomaly_ratio=round(anomaly_ratio, 6),
            training_time_seconds=result.training_time_seconds,
            artifacts_saved=artifacts_saved,
        )