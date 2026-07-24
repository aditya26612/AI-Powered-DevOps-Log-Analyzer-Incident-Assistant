"""
===============================================================================
DevInsight AI - Training Result
===============================================================================

Represents the output of the training phase.

This object contains only information directly produced during model training.

Evaluation metrics are computed separately by Evaluator.

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

from dataclasses import dataclass

from sklearn.ensemble import IsolationForest


@dataclass(slots=True, frozen=True)
class TrainingResult:
    """
    Result produced immediately after model training.
    """

    model: IsolationForest

    feature_count: int

    sample_count: int

    training_time_seconds: float