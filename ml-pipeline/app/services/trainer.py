"""
===============================================================================
DevInsight AI - Trainer Service
===============================================================================

Trains the machine learning model.

Responsibilities
----------------
- Fit the model
- Produce TrainingResult

Does NOT:
- Read datasets
- Extract features
- Scale data
- Save artifacts

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import time

import numpy as np
from sklearn.ensemble import IsolationForest

from app.domain.training_result import TrainingResult


class Trainer:
    """
    Service responsible for model training.
    """

    def __init__(
        self,
        model: IsolationForest,
    ) -> None:

        self._model = model

    def train(
        self,
        X: np.ndarray,
    ) -> TrainingResult:
        """
        Train Isolation Forest.
        """

        start = time.perf_counter()

        self._model.fit(X)

        elapsed = time.perf_counter() - start

        predictions = self._model.predict(X)

        anomaly_count = np.sum(predictions == -1)

        anomaly_ratio = anomaly_count / len(predictions)

        return TrainingResult(
        model=self._model,
        feature_count=X.shape[1],
        sample_count=len(X),
        training_time_seconds=elapsed,
        )