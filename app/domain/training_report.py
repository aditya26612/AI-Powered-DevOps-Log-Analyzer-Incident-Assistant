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