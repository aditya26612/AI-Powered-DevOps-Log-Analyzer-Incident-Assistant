"""
===============================================================================
DevInsight AI - Preprocessing Service
===============================================================================

Provides reusable preprocessing operations for both model training and
prediction.

Responsibilities
----------------
- Fit preprocessing components
- Transform feature matrices
- Reuse fitted preprocessing during inference

Author : DevInsight AI
Version: 1.0.0
===============================================================================
"""

from __future__ import annotations

import numpy as np
from sklearn.preprocessing import StandardScaler


class PreprocessingService:
    """
    Handles preprocessing of feature matrices.
    """

    def __init__(
        self,
        scaler: StandardScaler,
    ) -> None:

        self._scaler = scaler

    @property
    def scaler(self) -> StandardScaler:
        """
        Return scaler instance.
        """

        return self._scaler

    def fit_transform(
        self,
        features: np.ndarray,
    ) -> np.ndarray:
        """
        Fit scaler during training.
        """

        return self._scaler.fit_transform(features)

    def transform(
        self,
        features: np.ndarray,
    ) -> np.ndarray:
        """
        Transform data using an already-fitted scaler.
        """

        return self._scaler.transform(features)