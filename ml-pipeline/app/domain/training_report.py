"""
===============================================================================
DevInsight AI - Training Report
===============================================================================

Represents the final outcome of the complete training pipeline.

Unlike TrainingResult, this includes artifact and pipeline information.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True, frozen=True)
class TrainingReport:
    """
    Final report produced after a successful training pipeline execution.
    """

    sample_count: int

    feature_count: int

    anomaly_count: int

    anomaly_ratio: float

    training_time_seconds: float

    artifacts_saved: bool

    precision: float | None = None

    recall: float | None = None

    f1_score: float | None = None

    f2_score: float | None = None

    roc_auc: float | None = None

    pr_auc: float | None = None

    false_positive_rate: float | None = None

    false_alerts_per_1000: float | None = None

    test_sample_count: int | None = None

    confusion_matrix: dict[str, int] | None = None